package org.apache.maven.surefire.its.jiras;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.surefire.its.fixture.HelperAssertions;
import org.apache.maven.surefire.its.fixture.IntegrationTestSuiteResults;
import org.apache.maven.surefire.its.fixture.OutputValidator;
import org.apache.maven.surefire.its.fixture.SurefireJUnit4IntegrationTestCase;
import org.apache.maven.surefire.its.fixture.SurefireLauncher;
import static org.junit.Assert.*;
import org.junit.Test;

public class Surefire1295AttributeJvmCrashesToTestsIT
    extends SurefireJUnit4IntegrationTestCase
{
    @Test
    public void crashInFork()
    {
        checkCrashTypes(unpack( "crash-during-test" ), 1);
    }
    
    @Test
    public void crashInSingleUseFork()
    {
        checkCrashTypes(unpack( "crash-during-test" ).forkCount(1).reuseForks(false), 2);
    }

    @Test
    public void crashInReusableFork()
    {
        checkCrashTypes(unpack( "crash-during-test" ).forkOncePerThread().threadCount( 1 ), 1);
    }

    void checkCrashTypes(SurefireLauncher launcher, int expectedTotal) {
        checkCrash( launcher.addGoal( "-DcrashType=exit" ), expectedTotal);
        checkCrash( launcher.addGoal( "-DcrashType=abort" ), expectedTotal);
        checkCrash( launcher.addGoal( "-DcrashType=segfault" ), expectedTotal);
    }

    void checkCrash(SurefireLauncher launcher, int expectedTotal) {
        OutputValidator ov = launcher.maven().withFailure().executeTest()
                .verifyTextInLog("Crashed tests:")
                .verifyTextInLog("junit44.environment.BasicTest")
                .verifyTextInLog("The forked VM terminated without properly saying goodbye. VM crash or System.exit called?");
    }
}
