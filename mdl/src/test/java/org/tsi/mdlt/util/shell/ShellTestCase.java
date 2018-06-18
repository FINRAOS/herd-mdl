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
package org.tsi.mdlt.util.shell;

import java.util.HashMap;
import java.util.Map;

//TODO add attribute: cleanup like delete namespace/definition etc
public class ShellTestCase {

    private final String name;
    private final String description;
    private final String command;

    private String[] arguments;
    private String workingDirectory;
    private Map<String, String> environment;
    private int waitAfterCompletion;
    private String assertVal;

    public ShellTestCase(String name, String description, String command) {
        this.name = name;
        this.description = description;
        this.command = command;
        this.arguments = null;
        this.workingDirectory = "~";
        this.environment = new HashMap<>();
        this.waitAfterCompletion = 0;
        this.assertVal = null;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCommand() {
        return command;
    }

    public String[] getArguments() {
        return arguments;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public int getWaitAfterCompletion() {
        return waitAfterCompletion;
    }

    public String getAssertVal() {
        return assertVal;
    }

    public void setArguments(String[] arguments) {
        this.arguments = arguments;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    public void setWaitAfterCompletion(int waitForCompletion) {
        if (waitForCompletion < 0) {
            waitForCompletion = 0;
        }
        this.waitAfterCompletion = waitForCompletion;
    }

    public void setAssertVal(String assertVal) {
        this.assertVal = assertVal;
    }
}
