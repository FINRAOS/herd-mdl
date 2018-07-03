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
package org.tsi.mdlt.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import com.amazonaws.services.cloudformation.model.AlreadyExistsException;
import com.amazonaws.services.cloudformation.model.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.aws.CloudFormationClient;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.enums.StackOutputKeyEnum;

public class TestWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String SETUP_CMD = "setup";
    private static final String SHUTDOWN_CMD = "shutdown";

    private static final String APP_STACK_TEMPLATE_CFT = "InstallMDL.yml";

    private static final String OUTPUT_NAME_HERD_HOSTNAME = "HerdHostname";
    private static final String OUTPUT_NAME_HERD_URL = "HerdURL";

    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                LOGGER.error("Usage: TestWrapper <setup|shutdown>");
                System.exit(1);
            }
            //Getting properties
            Properties testProperties = TestProperties.getProperties();
            String instanceName = testProperties.getProperty(StackInputParameterKeyEnum.MDL_INSTANCE_NAME.getKey());
            boolean rollbackOnFailure = Boolean.valueOf(System.getProperty(StackInputParameterKeyEnum.ROLLBACK_ON_FAILURE.getKey()));
            String command = args[0];

            if (SETUP_CMD.equals(command)) {
                LOGGER.info("Create application wrapper stack, wait for completion and save outputs for testing");
                createStackAndWaitForCompletion(instanceName, rollbackOnFailure);
                saveStackInputProperties(instanceName);
                saveStackOutputProperties(instanceName);
            }
            else if (SHUTDOWN_CMD.equals(command)) {
                //Note: Delete app wrapper stack first, then prereq wrapper stack
                shutdownStack(instanceName);
            }
            else {
                throw new IllegalArgumentException("Unrecognized command : " + command);
            }
            System.exit(0);
        }
        catch (Exception e) {
            LOGGER.error("Something went wrong while running tests.", e);
            System.exit(1);
        }
    }

    private static void createStackAndWaitForCompletion(String instanceName, boolean rollbackOnFailure) {
        String stackName = getStackNameByInstanceName(instanceName);
        CloudFormationClient cfClient;
        try {
            cfClient = new CloudFormationClient(stackName);
            Map<String, String> parameters = createStackParameters();
            try {
                cfClient.createStack(parameters, APP_STACK_TEMPLATE_CFT, false, rollbackOnFailure);
            }
            catch (AlreadyExistsException ae) {
                LOGGER.warn("Stack, " + stackName + " already exists, initializing the properties file if needed");
            }
        }
        catch (Exception e) {
            LOGGER.error("Error while trying to create the stack.", e);
            System.exit(1);
        }
    }

    private static Map<String, String> createStackParameters() {
        Properties testProperties = TestProperties.getProperties();
        String enableSslAndAuth = testProperties.getProperty(StackInputParameterKeyEnum.ENABLE_SSL_AUTH.getKey());

        Map<String, String> parameters = new HashMap<>();
        parameters.put(StackInputParameterKeyEnum.CREATE_DEMO_OBJECT.getKey(), "true");
        addTestInputPropertyToParameterMap(StackInputParameterKeyEnum.MDL_INSTANCE_NAME, parameters);
        addTestInputPropertyToParameterMap(StackInputParameterKeyEnum.ENVIRONMENT, parameters);
        addTestInputPropertyToParameterMap(StackInputParameterKeyEnum.DEPLOY_COMPONENTS, parameters);
        addTestInputPropertyToParameterMap(StackInputParameterKeyEnum.RELEASE_VERSION, parameters);
        addTestInputPropertyToParameterMap(StackInputParameterKeyEnum.ENABLE_SSL_AUTH, parameters);
        //when enableSslAndAuth is true, set parameter createOpenLdap to true
        parameters.put(StackInputParameterKeyEnum.CREATE_OPEN_lDAP.getKey(), enableSslAndAuth);
        return parameters;
    }

    private static void addTestInputPropertyToParameterMap(StackInputParameterKeyEnum keyEnum, Map<String, String> parameters){
        parameters.put(keyEnum.getKey(),  TestProperties.getProperties().getProperty(keyEnum.getKey()));
    }

    private static void saveStackOutputProperties(String instanceName) throws Exception {

        LOGGER.info("Save stack outputs to file test.props for instanceName: " + instanceName);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File("mdlt/conf/test.props"), true))) {

            LOGGER.info("Get all output value from wrapper and nested stacks and write them to test.props");

            writer.write(StackOutputKeyEnum.MDL_INSTANCE_NAME.getKey() + "=" + instanceName);
            writer.newLine();

            String appStackName = getStackNameByInstanceName(instanceName);
            writer.write(StackOutputKeyEnum.APP_STACK_NAME.getKey() + "=" + appStackName);
            writer.newLine();

            CloudFormationClient cfClient = new CloudFormationClient(appStackName);
            Map<String, String> clusterOutputs = cfClient.getStackOutput();

            if (clusterOutputs != null && !clusterOutputs.isEmpty()) {
                for (Entry<String, String> entry : clusterOutputs.entrySet()) {
                    writeEntryToWriter(entry, writer);
                }
            }
        }
    }

    private static void saveStackInputProperties(String instanceName) throws Exception {
        LOGGER.info("Save some stack inputs to file test.props for instanceName: " + instanceName);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File("mdlt/conf/test.props")))) {
            String appStackName = getStackNameByInstanceName(instanceName);

            CloudFormationClient cfClient = new CloudFormationClient(appStackName);
            Parameter env = cfClient.getStackByName(appStackName).getParameters().stream()
                    .filter(parameter -> parameter.getParameterKey().equals(StackInputParameterKeyEnum.ENVIRONMENT.getKey()))
                    .findFirst().get();

            writer.write(env.getParameterKey() + "=" + env.getParameterValue());
            writer.newLine();
        }
    }

    private static void writeEntryToWriter(Entry<String, String> entry, BufferedWriter writer)
            throws IOException {
        switch (entry.getKey()) {
            case OUTPUT_NAME_HERD_URL:
                String herdURLValue = entry.getValue();
                writer.write(entry.getKey() + "=" + herdURLValue);
                writer.newLine();

                writer.write(OUTPUT_NAME_HERD_HOSTNAME + "="
                        + herdURLValue.substring(0, herdURLValue.indexOf("/herd-app")));
                writer.newLine();
                break;
            default:
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
        }
    }

    private static void shutdownStack(String instanceName) throws Exception {
        String stackName = getStackNameByInstanceName(instanceName);
        CloudFormationClient cfClient = new CloudFormationClient(stackName);
        cfClient.deleteStack();
    }

    private static String getStackNameByInstanceName(String instanceName) {
        return instanceName + "App";
    }
}
