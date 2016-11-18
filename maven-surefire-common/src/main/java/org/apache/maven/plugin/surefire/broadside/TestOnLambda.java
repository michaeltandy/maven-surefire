package org.apache.maven.plugin.surefire.broadside;

import com.amazonaws.services.lambda.invoke.LambdaFunction;
import uk.me.mjt.broadside.TestResult;
import uk.me.mjt.broadside.TestSettings;

public interface TestOnLambda {
    @LambdaFunction(functionName = "javatest")
    TestResult performTest(TestSettings input);
}
