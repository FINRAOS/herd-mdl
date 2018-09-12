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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import com.amazonaws.services.cloudformation.model.AlreadyExistsException;
import com.amazonaws.services.cloudformation.model.Parameter;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.aws.CloudFormationClient;
import org.tsi.mdlt.aws.SsmUtil;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.enums.StackOutputKeyEnum;

public class TestWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String SETUP_CMD = "setup";
    private static final String SHUTDOWN_CMD = "shutdown";

    private static final String APP_STACK_TEMPLATE_CFT = "InstallMDL.yml";

    private static final String OUTPUT_NAME_HERD_HOSTNAME = "HerdHostname";
    private static final String OUTPUT_NAME_HERD_URL = "HerdURL";
    private static final Properties TEST_PROPERTIES = TestProperties.getProperties();

    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                LOGGER.error("Usage: TestWrapper <setup|shutdown>");
                System.exit(1);
            }
            //Getting properties
            String instanceName = TEST_PROPERTIES.getProperty(StackInputParameterKeyEnum.MDL_INSTANCE_NAME.getKey());
            String stackName = TEST_PROPERTIES.getProperty(StackInputParameterKeyEnum.MDL_STACK_NAME.getKey());
            boolean rollbackOnFailure = Boolean.valueOf(System.getProperty(StackInputParameterKeyEnum.ROLLBACK_ON_FAILURE.getKey()));
            String command = args[0];

            if (SETUP_CMD.equals(command)) {
                LOGGER.info("Create VPC SSM if creating new mdl stack");
                createVpcSsmIfNotExistingStack(stackName, instanceName);

                LOGGER.info("Create application wrapper stack, wait for completion and save outputs for testing");
                createStackAndWaitForCompletion(stackName, rollbackOnFailure);
                saveStackInputProperties(stackName);
                saveStackOutputProperties(stackName);
            }
            else if (SHUTDOWN_CMD.equals(command)) {
                deleteVpcSsm(instanceName);
                shutdownStack(stackName);
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

    private static void createStackAndWaitForCompletion(String stackName, boolean rollbackOnFailure) {
        CloudFormationClient cfClient;
        try {
            cfClient = new CloudFormationClient(stackName);
            Map<String, String> parameters = createStackParameters();
            try {
                cfClient.createStack(parameters, APP_STACK_TEMPLATE_CFT, rollbackOnFailure);
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

    private static void createVpcSsmIfNotExistingStack(String stackName, String instanceName) throws Exception {
        boolean existingStack = true;
        if (!new CloudFormationClient(stackName).stackExists(stackName)) {
            String environment = TEST_PROPERTIES.getProperty(StackInputParameterKeyEnum.ENVIRONMENT.getKey());

            String vpcKeyFormat = "/global/%s/%s/VPC/ID";
            String privateSubnetsKeyFormat = "/global/%s/%s/VPC/SubnetIDs/private";
            String publicSubnetsKeyFormat = "/global/%s/%s/VPC/SubnetIDs/public";
            String vpcKey = String.format(vpcKeyFormat, instanceName, environment);
            String privateSubnetsKey = String.format(privateSubnetsKeyFormat, instanceName, environment);
            String publicSubnetsKey = String.format(publicSubnetsKeyFormat, instanceName, environment);

            String mdltWrapperInstanceName = TEST_PROPERTIES.getProperty("MDLTWrapperInstanceName");
            String vpcValue = TEST_PROPERTIES.getProperty(StackInputParameterKeyEnum.MDL_VPC_ID.getKey());
            String privateSubnetsValue = TEST_PROPERTIES.getProperty(StackInputParameterKeyEnum.MDL_PRIVATE_SUBNETS.getKey());
            String publicSubnetsValue = TEST_PROPERTIES.getProperty(StackInputParameterKeyEnum.MDL_PUBLIC_SUBNETS.getKey());

            if (BooleanUtils.toBoolean(TEST_PROPERTIES.getProperty(StackInputParameterKeyEnum.CREATE_VPC.getKey()))) {
                vpcValue = SsmUtil.getPlainParameter(String.format(vpcKeyFormat, mdltWrapperInstanceName, environment)).getValue();
                privateSubnetsValue = SsmUtil.getPlainParameter(String.format(privateSubnetsKeyFormat, mdltWrapperInstanceName, environment)).getValue();
                publicSubnetsValue = SsmUtil.getPlainParameter(String.format(publicSubnetsKeyFormat, mdltWrapperInstanceName, environment)).getValue();
            }
            SsmUtil.putParameter(vpcKey, vpcValue);
            SsmUtil.putParameter(privateSubnetsKey, privateSubnetsValue);
            SsmUtil.putParameter(publicSubnetsKey, publicSubnetsValue);
            existingStack = false;
        }

        LOGGER.info(String.format("Save existingStack=%s to file test.props", String.valueOf(existingStack)));
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File("mdlt/conf/test.props")));
        writer.write("existingStack" + "=" + String.valueOf(existingStack));
        writer.newLine();
        writer.close();
    }

    private static void deleteVpcSsm(String instanceName) throws Exception {
        String environment = TEST_PROPERTIES.getProperty(StackInputParameterKeyEnum.ENVIRONMENT.getKey());
        String vpcKeyFormat = "/global/%s/%s/VPC/ID";
        String privateSubnetsKeyFormat = "/global/%s/%s/VPC/SubnetIDs/private";
        String publicSubnetsKeyFormat = "/global/%s/%s/VPC/SubnetIDs/public";
        String vpcKey = String.format(vpcKeyFormat, instanceName, environment);
        String privateSubnetsKey = String.format(privateSubnetsKeyFormat, instanceName, environment);
        String publicSubnetsKey = String.format(publicSubnetsKeyFormat, instanceName, environment);

        SsmUtil.deleteParameter(vpcKey);
        SsmUtil.deleteParameter(privateSubnetsKey);
        SsmUtil.deleteParameter(publicSubnetsKey);
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

        addTestInputPropertyToParameterMap(StackInputParameterKeyEnum.DOMAIN_NAME_SUFFIX, parameters);
        addTestInputPropertyToParameterMap(StackInputParameterKeyEnum.HOSTED_ZONE_NAME, parameters);
        addTestInputPropertyToParameterMap(StackInputParameterKeyEnum.CERTIFICATE_ARN, parameters);

        addTestInputPropertyToParameterMap(StackInputParameterKeyEnum.ENABLE_SSL_AUTH, parameters);
        //when enableSslAndAuth is true, set parameter createOpenLdap to true
        parameters.put(StackInputParameterKeyEnum.CREATE_OPEN_lDAP.getKey(), enableSslAndAuth);
        //DeployHostEc2 will never createVpc, mdlt wrapper yml file(mdlt.yml) will create vpc if createVpc is true
        parameters.put(StackInputParameterKeyEnum.CREATE_VPC.getKey(), "false");
        return parameters;
    }

    private static void addTestInputPropertyToParameterMap(StackInputParameterKeyEnum keyEnum, Map<String, String> parameters) {
        parameters.put(keyEnum.getKey(), TestProperties.getProperties().getProperty(keyEnum.getKey()));
    }

    private static void saveStackOutputProperties(String stackName) throws Exception {

        LOGGER.info("Save stack outputs to file test.props for stack: " + stackName);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File("mdlt/conf/test.props"), true))) {

            LOGGER.info("Get all output value from wrapper and nested stacks and write them to test.props");
            writer.write(StackOutputKeyEnum.APP_STACK_NAME.getKey() + "=" + stackName);
            writer.newLine();

            CloudFormationClient cfClient = new CloudFormationClient(stackName);
            Map<String, String> clusterOutputs = cfClient.getStackOutput();

            if (clusterOutputs != null && !clusterOutputs.isEmpty()) {
                for (Entry<String, String> entry : clusterOutputs.entrySet()) {
                    writeEntryToWriter(entry, writer);
                }
            }
        }
    }

    private static void saveStackInputProperties(String stackName) throws Exception {
        LOGGER.info("Save some stack inputs to file test.props for stack: " + stackName);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File("mdlt/conf/test.props"), true))) {

            CloudFormationClient cfClient = new CloudFormationClient(stackName);
            List<Parameter> stackInputParameters = cfClient.getStackByName(stackName).getParameters();
            Parameter env = stackInputParameters.stream()
                .filter(parameter -> parameter.getParameterKey().equals(StackInputParameterKeyEnum.ENVIRONMENT.getKey()))
                .findFirst().get();

            writer.write(env.getParameterKey() + "=" + env.getParameterValue());
            writer.newLine();

            Parameter instanceName = stackInputParameters.stream()
                .filter(parameter -> parameter.getParameterKey().equals(StackInputParameterKeyEnum.MDL_INSTANCE_NAME.getKey()))
                .findFirst().get();
            writer.write(StackInputParameterKeyEnum.MDL_INSTANCE_NAME.getKey() + "=" + instanceName.getParameterValue());
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

    //TODO, need to retry only on specific errors for deleting stack
    private static void shutdownStack(String stackName) throws Exception {
        CloudFormationClient cfClient = new CloudFormationClient(stackName);
        int retryTimes = 3;
        while (retryTimes > 0) {
            try {
                cfClient.deleteStack();
                break;
            }
            catch (Exception e) {
                retryTimes--;
                LOGGER.error("Failed to delete stack, remaining retry times :" + retryTimes);
            }
        }
    }

}
