
package org.apache.maven.plugin.surefire.broadside;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectResult;
import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.zip.*;
import org.apache.maven.plugin.surefire.booterclient.ForkConfiguration;
import org.apache.maven.surefire.booter.ProviderConfiguration;
import org.apache.maven.surefire.booter.StartupConfiguration;
import org.apache.maven.surefire.testset.TestRequest;


public class BundleMaker {
    private static final FileAttribute OWNER_RW_ONLY = PosixFilePermissions.asFileAttribute(
            PosixFilePermissions.fromString("rw-------"));
    
    public String makeUploadToS3(final StartupConfiguration startupConfiguration,
            final ForkConfiguration forkConfiguration,
            final ProviderConfiguration providerConfiguration) {
        try {
            File f = makeBundleInternal(startupConfiguration, forkConfiguration, providerConfiguration);
            
            AmazonS3 s3Client = new AmazonS3Client();
            PutObjectResult por = s3Client.putObject("broadside-mjt", f.getName(), f);
            
            return "s3://broadside-mjt/"+f.getName();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    /*public File makeBundle(final StartupConfiguration startupConfiguration,
            final ForkConfiguration forkConfiguration) {
        try {
            return makeBundleInternal(startupConfiguration, forkConfiguration);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }*/
    
    private File makeBundleInternal(final StartupConfiguration startupConfiguration,
            final ForkConfiguration forkConfiguration,
            final ProviderConfiguration providerConfiguration) throws IOException {
        
        SortedSet<String> classpath = generateClasspath(startupConfiguration, forkConfiguration);
        SortedSet<String> directories = findDirectories(classpath);
        SortedSet<String> nonDirectories = findNonDirectories(classpath);
        
        // Files on the classpath will probably mostly be jars from ~/.m2
        // Folder on the classpath will probably be target/classes and target/test-classes
        final Map<String,File> fileTargets = new TreeMap();
        for (String filename : nonDirectories) {
            File f = new File(filename);
            fileTargets.put(f.getName(), f);
        }
        
        for (String dirName : directories) {
            final Path directory = new File(dirName).toPath();
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    String relativePath = directory.relativize(path).toString();
                    fileTargets.put(relativePath, path.toFile());
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        
        // Needed for the integration test "CheckTestNgSuiteXmlIT"
        // (which passes XML files to the surefire booter)
        TestRequest testSuiteDefinition = providerConfiguration.getTestSuiteDefinition();
        if ( testSuiteDefinition != null ) {
            for (File f : testSuiteDefinition.getSuiteXmlFiles()) {
                fileTargets.put("suiteXml/"+f.getName(), f);
            }
        }
        
        File f = zipFilesToOutput(fileTargets);
        System.out.println(f.getAbsolutePath());
        return f;
    }
    
    private SortedSet<String> generateClasspath(final StartupConfiguration startupConfiguration,
            final ForkConfiguration forkConfiguration) {
        
        SortedSet<String> classpath = new TreeSet();
        classpath.addAll(startupConfiguration.getClasspathConfiguration().getTestClasspath().getClassPath());
        classpath.addAll(startupConfiguration.getClasspathConfiguration().getProviderClasspath().getClassPath());
        if (!startupConfiguration.isProviderMainClass()) {
            classpath.addAll(forkConfiguration.getBootClasspath().getClassPath());
        }
        
        return classpath;
    }
    
    private SortedSet<String> findDirectories(Set<String> toCheck) {
        SortedSet<String> directories = new TreeSet();
        for (String s : toCheck) {
            if (new File(s).isDirectory()) {
                directories.add(s);
            }
        }
        return directories;
    }
    
    private SortedSet<String> findNonDirectories(Set<String> toCheck) {
        SortedSet<String> result = new TreeSet();
        for (String s : toCheck) {
            File f = new File(s);
            if (f.exists() && !f.isDirectory()) {
                result.add(s);
            }
        }
        return result;
    }
    
    private static File zipFilesToOutput(Map<String,File> source) throws IOException {
        File file = Files.createTempFile( "broadsidebundle", ".zip", OWNER_RW_ONLY).toFile();
        
        FileOutputStream fos = null;
        ZipOutputStream zos = null;
        try {
            fos = new FileOutputStream(file);
            zos = new ZipOutputStream(fos);
            // We add the files in sorted order and zero the time so our builds
            // are repeatable.
            for (String key : sortSet(source.keySet())) {
                ZipEntry ze = new ZipEntry(key);
                ze.setTime(0);
                zos.putNextEntry(ze);
                Files.copy(source.get(key).toPath(), zos);
                zos.closeEntry();
            }
        } finally {
            if (zos != null) zos.close();
            if (fos != null) fos.close();
        }
        return file;
    }
    
    private static <E> SortedSet<E> sortSet(Set<E> toSort) {
        return new TreeSet(toSort);
    }
    
    private static void unzipTo(File zipFile, File outputFolder) throws IOException {
        if (!outputFolder.isDirectory()) throw new IllegalArgumentException("Target folder must exist.");
        if (!zipFile.isFile()) throw new IllegalArgumentException("Source zip must exist.");
        
        //get the zip file content
    	ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new FileInputStream(zipFile));
            
            ZipEntry ze = zis.getNextEntry();
            
            while (ze != null) {
                File destination = new File(outputFolder.getAbsolutePath()+File.separator+ze.getName());
                if (!destination.exists()) {
                    destination.getParentFile().mkdirs();
                    Files.copy(zis, destination.toPath());
                }
                ze = zis.getNextEntry();
            }
            
        } finally {
            if (zis != null) {
                zis.close();
            }
        }

    }

}
