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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.enums.StackOutputKeyEnum;
import org.tsi.mdlt.util.StackOutputPropertyReader;
import org.tsi.mdlt.util.TestProperties;
import org.tsi.mdlt.util.shell.ShellCommandProperty;
import org.tsi.mdlt.util.shell.ShellHelper;

/**
 * Test class to verify authentication parameter is working as expected on both with auth and
 * without auth
 */

public class HttpsAndAuthTest extends BaseTest {

    private static final String COMPONENT = "Herd Http And Auth Test";

    private static Map<String, String> envVars = ShellCommandProperty.getPropertiesMap();
    private static final String HERD_AUTH_TESTCASES = "/testCases/auth/herdAuthTestCases.json";
    private static final String HTTP_TESTCASES = "/testCases/auth/httpTestCases.json";
    private static final String HTTPS_TESTCASES = "/testCases/auth/httpsTestCases.json";

    private static final boolean IS_SSLAUTH_ENABLED = Boolean.valueOf(TestProperties.get(StackInputParameterKeyEnum.ENABLE_SSL_AUTH));

    @TestFactory
    public Stream<DynamicTest> testHerdShellCommandWithWrongCredential() {
        String assertionPass = "status code is 200";
        String assertionFailure = "status code is 401";

        return getTestCases(HERD_AUTH_TESTCASES)
                .stream().map(command -> DynamicTest.dynamicTest(COMPONENT + command.getName(), () -> {
                    String result = ShellHelper.executeShellTestCase(command, envVars);
                    if (!IS_SSLAUTH_ENABLED) {
                        LogVerification("Verify herd shell command response pass without/with invalid credential provided when enableAuth is false");
                        assertThat(result, containsString(assertionPass));
                    }
                    else {
                        LogVerification("Verify herd shell command response failure without/with invalid credential provided when enableAuth is true");
                        assertThat(result, containsString(assertionFailure));
                    }
                }));
    }

    @Test
    public void testOutputUrlHttpAndHttpsPrefix() {
        String httpsPrefix = "https";
        String httpPrefix = "http";
        String bdsqlAuthSuffix = "443/hive";
        String bdsqlNoAuthSuffix = "80/hive";
        if (IS_SSLAUTH_ENABLED) {
            LogVerification("Verify output url prefix is :" + httpsPrefix);
            assertTrue(StackOutputPropertyReader.get(StackOutputKeyEnum.HERD_LB_URL).startsWith(httpsPrefix), "Herd LB url is not started with https");
            assertTrue(StackOutputPropertyReader.get(StackOutputKeyEnum.HERD_URL).startsWith(httpsPrefix), "Herd URL is not started with https");
            assertTrue(StackOutputPropertyReader.get(StackOutputKeyEnum.SHEPHERD_URL).startsWith(httpsPrefix), "Shepherd URl is not started with https");
            assertTrue(StackOutputPropertyReader.get(StackOutputKeyEnum.BDSQL_URL).endsWith(bdsqlAuthSuffix), "Bdsq URl is not ends with" + bdsqlAuthSuffix);
        }
        else {
            LogVerification("Verify output url prefix is :" + httpPrefix);
            assertTrue(StackOutputPropertyReader.get(StackOutputKeyEnum.HERD_LB_URL).startsWith(httpPrefix), "Herd LB url is not started with http");
            assertTrue(StackOutputPropertyReader.get(StackOutputKeyEnum.HERD_URL).startsWith(httpPrefix), "Herd URL is not started with http");
            assertTrue(StackOutputPropertyReader.get(StackOutputKeyEnum.SHEPHERD_URL).startsWith(httpPrefix), "Shepherd URl is not started with http:");
            assertTrue(StackOutputPropertyReader.get(StackOutputKeyEnum.BDSQL_URL).endsWith(bdsqlNoAuthSuffix), "Bdsq URl is not correct");
        }
    }

    @TestFactory
    public Stream<DynamicTest> testHerdHttpIsNotAllowedWhenHttpsEnabled() {
        return getTestCases(HTTP_TESTCASES)
                .stream().map(command -> DynamicTest.dynamicTest(COMPONENT + command.getName(), () -> {
                    if (!IS_SSLAUTH_ENABLED) {
                        LogVerification("Verify herd/shepherd call pass with http url when enableSslAuth is false");
                        String result = ShellHelper.executeShellTestCase(command, envVars);
                        assertThat(command.getAssertVal(), notNullValue());
                        assertThat(result, containsString(command.getAssertVal()));
                    }
                    else {
                        LogVerification("Verify herd/shepherd call failed with http url when enableSslAuth is true");
                        assertThrows(RuntimeException.class, () -> {
                            ShellHelper.executeShellTestCase(command, envVars);
                        });
                    }
                }));
    }

    @TestFactory
    public Stream<DynamicTest> testHerdHttpsIsNotAllowedWhenHttpsIsDisabled() {
        return getTestCases(HTTPS_TESTCASES)
                .stream().map(command -> DynamicTest.dynamicTest(COMPONENT + command.getName(), () -> {
                    if (IS_SSLAUTH_ENABLED) {
                        LogVerification("Verify herd call pass with https url when enableSslAuth is true");
                        String result = ShellHelper.executeShellTestCase(command, envVars);
                        assertThat(command.getAssertVal(), notNullValue());
                        assertThat(result, containsString(command.getAssertVal()));
                    }
                    else {
                        LogVerification("Verify herd call failed with https url when enableSslAuth is false");
                        assertThrows(RuntimeException.class, () -> {
                            ShellHelper.executeShellTestCase(command, envVars);
                        });
                    }
                }));
    }
}
