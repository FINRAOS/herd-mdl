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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ShellHelper {

    public static List<ShellTestCase> parseShellTestCases(String testCasesFile) throws IOException {
        List<ShellTestCase> shellTestCases = new ArrayList<>();
        // Parse the JSON and populate the test cases
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonTestCases = mapper.readTree(ShellHelper.class.getResourceAsStream(testCasesFile));
        for (JsonNode jsonTestCase : jsonTestCases) {
            // Print the json test case we are parsing
            printJsonTestCase(jsonTestCase.toString());

            // Extract info
            //Note: can be used to ignore single testcase
            String name = jsonTestCase.get("name").asText();
            String description = jsonTestCase.get("description").asText();
            if (jsonTestCase.hasNonNull("ignore") && jsonTestCase.get("ignore").asBoolean()) {
                System.out.println("Ignoring test case --> " + name);
                continue;
            }

            JsonNode jsonTestDetails = jsonTestCase.get("testCase");
            String command = jsonTestDetails.get("command").asText();
            ShellTestCase shellTestCase = new ShellTestCase(name, description, command);
            if (jsonTestDetails.hasNonNull("arguments")) {
                JsonNode jsonArguments = jsonTestDetails.get("arguments");
                List<String> args = new ArrayList<>();
                for (JsonNode jsonArgument : jsonArguments) {
                    args.add(jsonArgument.asText());
                }
                shellTestCase.setArguments(args.toArray(new String[args.size()]));
            }
            if (jsonTestDetails.hasNonNull("workingDirectory")) {
                shellTestCase.setWorkingDirectory(jsonTestDetails.get("workingDirectory").asText());
            }
            if (jsonTestDetails.hasNonNull("environment")) {
                JsonNode jsonEnvironment = jsonTestDetails.get("environment");
                Iterator<String> fieldNames = jsonEnvironment.fieldNames();
                Map<String, String> environment = new HashMap<>();
                while (fieldNames.hasNext()) {
                    String fieldName = fieldNames.next();
                    environment.put(fieldName, jsonEnvironment.get(fieldName).asText());
                }
                shellTestCase.setEnvironment(environment);
            }
            if (jsonTestCase.hasNonNull("waitAfterCompletion")) {
                shellTestCase.setWaitAfterCompletion(jsonTestCase.get("waitAfterCompletion").asInt());
            }
            if (jsonTestCase.hasNonNull("assert")) {
                shellTestCase.setAssertVal(jsonTestCase.get("assert").asText());
            }
            shellTestCases.add(shellTestCase);
        }
        return shellTestCases;
    }

    //TODO executeShellTestCase is messed with substituteInputFiles when combine real request body env with property envs
    public static String executeShellTestCase(ShellTestCase shellTestCase, Map<String, String> env)
            throws IOException, InterruptedException {
        // Merge environment variables
        Map<String, String> envVars = new HashMap<>();
        envVars.putAll(shellTestCase.getEnvironment());
        if (env != null) {
            envVars.putAll(env);
        }
        // Substitute input files
        substituteInputFiles(envVars);
        return executeShellCommand(generateShellCommand(shellTestCase), envVars);
    }

    private static void substituteInputFiles(Map<String, String> env) throws IOException {
        // Find environment variables pointing to a file and replace the stubs
        // in those files with actual values before running the test
        for (Entry<String, String> entry : env.entrySet()) {
            try {
                Path fsPath = Paths.get(entry.getValue());
                if (!Files.isRegularFile(fsPath)) {
                    continue;
                }
                System.out.println("Substituting file, '" + fsPath + "'");
                String content = new String(Files.readAllBytes(fsPath));

                // Make a copy of the original file
                fsPath = Paths.get(entry.getValue() + ".original");
                System.out.println("Creating a backup in, '" + fsPath + "'");
                Files.write(fsPath, content.getBytes());

                // Replace environment variables
                for (Entry<String, String> tmp : env.entrySet()) {
                    String regex = "\\$" + tmp.getKey();
                    content = content.replaceAll(regex, tmp.getValue());
                }

                // Write the substituted values to the file
                fsPath = Paths.get(entry.getValue());
                Files.write(fsPath, content.getBytes());
            }
            catch (InvalidPathException e) {
            }
        }
    }

    public static String generateShellCommand(ShellTestCase shellTestCase) {
        StringBuilder shellCommand = new StringBuilder();
        shellCommand.append("./mdlt/scripts/sh/shellExecutor.sh");
        shellCommand.append(" -w ").append(shellTestCase.getWorkingDirectory());
        shellCommand.append(" -c ").append(shellTestCase.getCommand());
        if (shellTestCase.getArguments() != null) {
            String arguments = String.join(" ", shellTestCase.getArguments());
            shellCommand.append(" -a \"").append(arguments).append("\"");
        }
        return shellCommand.toString();
    }

    public static String executeShellCommand(String command, Map<String, String> env)
            throws IOException, InterruptedException {
        // Print information about the command we are going to execute
        printCommandInfo(command, env);

        // Execute the command
        String shellCommand = "/bin/sh";
        String shellOption = "-c";
        String[] shellCommandWithArgument = {shellCommand, shellOption, command};
        ProcessBuilder shellProcessBuilder = new ProcessBuilder(shellCommandWithArgument);
        Map<String, String> shellEnv = shellProcessBuilder.environment();
        env = (env == null) ? new HashMap<>() : env;
        shellEnv.putAll(env);
        shellProcessBuilder.redirectErrorStream(true);
        Process currentCommandProcess = shellProcessBuilder.start(); // Start the process.

        // Read the stdout from the process
        InputStreamReader shellOutputReader = new InputStreamReader(
                currentCommandProcess.getInputStream());
        BufferedReader shellOutputBufferedReader = new BufferedReader(shellOutputReader);
        String outputLine;
        StringBuilder outputLog = new StringBuilder("");

        // Wait for the command and print the logs every 30 seconds
        while (!currentCommandProcess.waitFor(30, TimeUnit.SECONDS)) {
            // This gets printed while the command is running
            // Get the output/error log for the command
            while ((outputLine = shellOutputBufferedReader.readLine()) != null) {
                System.out.println(outputLine);
                outputLog.append(outputLine);
                outputLog.append(System.lineSeparator());
            }
        } // While loop

        // Read the remaining output
        while ((outputLine = shellOutputBufferedReader.readLine()) != null) {
            System.out.println(outputLine);
            outputLog.append(outputLine);
            outputLog.append(System.lineSeparator());
        }

        // Throw the exception if the command fails
        if (currentCommandProcess.exitValue() != 0) {
            // TODO: Escape all special characters used in printf
            // else JUnit report gets messed up
            String message = outputLog.toString();
            message = message.replace("%", "%%").replace("\\R", " | ");
            throw new RuntimeException(message);
        }

        return outputLog.toString().replace("%", "%%");
    }

    public static void printCommandInfo(String command, Map<String, String> env) {
        Stream.generate(() -> "-").limit(40).forEach(System.out::print);
        System.out.println();
        System.out.println("Command Information");
        Stream.generate(() -> "-").limit(40).forEach(System.out::print);
        System.out.println();

        System.out.println("Shell command");
        System.out.println();
        System.out.println(command);
        System.out.println();

        Stream.generate(() -> "-").limit(40).forEach(System.out::print);
        System.out.println();
    }

    public static void printJsonTestCase(String jsonTestCase) {
        Stream.generate(() -> "-").limit(40).forEach(System.out::print);
        System.out.println();
        System.out.println("Test Case");
        Stream.generate(() -> "-").limit(40).forEach(System.out::print);
        System.out.println();
        System.out.println(jsonTestCase);
        Stream.generate(() -> "-").limit(40).forEach(System.out::print);
        System.out.println();
    }
}
