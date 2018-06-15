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

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.tsi.mdlt.util.shell.ShellCommandProperty;

public class ShepherdTest extends BaseTest {

    private static final String COMPONENT = "Shepherd";
    private static final String SHEPHERD_TESTCASES_FILE = "/testCases/shepherd/testCases.json";
    private static final String SHEPHERD_TEST_HTML = "shepherd-test.html";

    private static Map<String, String> envVars = ShellCommandProperty.getPropertiesMap();


    @BeforeAll
    public static void setUp() throws IOException, InterruptedException {
        TestSetup.setUpShepherd(SHEPHERD_TEST_HTML, envVars);
    }

    @TestFactory
    public Stream<DynamicTest> testShellCommandsShepherdTest() {
        return generateDynamicShellTestCases(COMPONENT, SHEPHERD_TESTCASES_FILE, envVars);
    }

    @AfterAll
    public static void shutDown() {
    }

}
