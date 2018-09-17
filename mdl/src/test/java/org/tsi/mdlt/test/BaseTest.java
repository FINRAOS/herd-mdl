/*
 * Copyright 2018 herd-mdl contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
**/
package org.tsi.mdlt.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.util.jdbc.JDBCHelper;
import org.tsi.mdlt.util.jdbc.JDBCTestCase;
import org.tsi.mdlt.util.shell.ShellHelper;
import org.tsi.mdlt.util.shell.ShellTestCase;

public class BaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    protected void LogStep(String message) {
        LOGGER.info("Step: " + message);
    }

    protected void LogCleanup(String message) {
        LOGGER.info("Cleanup: " + message);
    }

    protected void LogVerification(String message) {
        LOGGER.info("Verification: " + message);
    }

    protected Stream<DynamicTest> generateDynamicShellTestCases(String component, String filePath,
            Map<String, String> envVars) {
        List<ShellTestCase> testCases = getTestCases(filePath);
        return verifyShellTestCases(component, testCases, envVars);
    }

    protected Stream<DynamicTest> generateDynamicJdbcTestCases(String component, String filePath,
            Map<String, String> envVars) {
        List<JDBCTestCase> testCases = getJdbcTestCases(filePath);
        return verifyJdbcTestCases(component, testCases, envVars);
    }

    protected List<ShellTestCase> getTestCases(String filePath) {
        ArrayList<ShellTestCase> inputCommands = new ArrayList<>();
        try {
            inputCommands.addAll(ShellHelper.parseShellTestCases(filePath));
        }
        catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to find testcases from filepath: " + filePath);
        }
        LogStep("Commands found -> " + inputCommands.size());
        return inputCommands;
    }

    protected List<JDBCTestCase> getJdbcTestCases(String filePath) {
        ArrayList<JDBCTestCase> inputCommands = new ArrayList<>();
        try {
            inputCommands.addAll(JDBCHelper.parseJDBCTestCases(filePath));
        }
        catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to find testcases from filepath: " + filePath);
        }
        LogStep("Commands found -> " + inputCommands.size());
        return inputCommands;
    }

    private Stream<DynamicTest> verifyShellTestCases(String component, List<ShellTestCase> testCases,
            Map<String, String> envVars) {
        return testCases.stream()
                .map(command -> DynamicTest.dynamicTest(component + " : " + command.getName(), () -> {
                    LogVerification("Verify shell command response");
                    String result = ShellHelper.executeShellTestCase(command, envVars);
                    assertThat(command.getAssertVal(), notNullValue());
                    assertThat(result, containsString(command.getAssertVal()));

                    sleepAfterCompletion(command.getName(), command.getWaitAfterCompletion());
                }));
    }

    private Stream<DynamicTest> verifyJdbcTestCases(String component, List<JDBCTestCase> testCases,
            Map<String, String> envVars) {
        return testCases.stream()
                .map(command -> DynamicTest.dynamicTest(component + " : " + command.getName(), () -> {
                    LogVerification("Verify shell command response");
                    List<String> result = JDBCHelper.executeJDBCTestCase(command, envVars);
                    assertThat(command.getAssertVal(), notNullValue());
                    List<String> expectedResult = Arrays.asList(command.getAssertVal());
                    assertThat(result,
                            containsInAnyOrder(expectedResult.toArray(new String[expectedResult.size()])));

                    sleepAfterCompletion(command.getName(), command.getWaitAfterCompletion());
                }));
    }

    private void sleepAfterCompletion(String commandName, int waitAfterCompletion)
            throws InterruptedException {
        if (waitAfterCompletion != 0) {
            LogStep("Test case '" + commandName + "' completed, waiting after completion for "
                    + waitAfterCompletion + " millisecond(s)");
            Thread.sleep(waitAfterCompletion);
        }
    }
}
