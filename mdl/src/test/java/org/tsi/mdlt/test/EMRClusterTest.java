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

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Stack;
import java.util.stream.Stream;

import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.model.AlreadyExistsException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import org.apache.commons.lang3.BooleanUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.aws.CloudFormationClient;
import org.tsi.mdlt.aws.SsmUtil;
import org.tsi.mdlt.enums.SsmParameterKeyEnum;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.enums.StackOutputKeyEnum;
import org.tsi.mdlt.util.StackOutputPropertyReader;
import org.tsi.mdlt.util.TestProperties;
import org.tsi.mdlt.util.shell.ShellCommandProperty;

public class EMRClusterTest extends BaseTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String COMPONENT = "EMR Cluster";

    private static final String EMR_PREREQ_STACK_SUFFIX = "EmrPreReqStack";
    private static final String EMR_CLUSTER_PREREQ_CFT = "mdltClusterPrereq.yml";

    private static CloudFormationClient cftClient;
    private static Map<String, String> envVars = ShellCommandProperty.getPropertiesMap();

    private static final String EMR_TESTCASES = "/testCases/herd/cluster/testCases.json";

    @BeforeAll
    public static void setUp() throws Exception {
        LOGGER.info("Working directory   =    " + System.getProperty("user.dir"));

        String instanceName = StackOutputPropertyReader.get(StackOutputKeyEnum.MDL_INSTANCE_NAME).toLowerCase();
        String stackName = instanceName + "-" + EMR_PREREQ_STACK_SUFFIX;
        cftClient = new CloudFormationClient(stackName);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("MDLInstanceName", instanceName);
        parameters.put("VpcId", SsmUtil.getPlainVpcParameter(SsmParameterKeyEnum.VPC_ID).getValue());
        parameters.put("SubnetId", SsmUtil.getPlainVpcParameter(SsmParameterKeyEnum.PRIVATE_SUBNETS).getValue());
        parameters.put("KeyPairName", TestProperties.getProperties().getProperty(StackInputParameterKeyEnum.KEY_PAIR_NAME.getKey()));

        try {
            cftClient.createStack(parameters, EMR_CLUSTER_PREREQ_CFT, true);
        }
        catch (AlreadyExistsException e) {
            LOGGER.info("Stack already exist, reuse existing running stack");
        }

        // Get all the output values from the provisioned stack and add
        // them as environment variables.
        Map<String, String> emrPrereqOutputs = cftClient.getStackOutput();
        envVars.putAll(emrPrereqOutputs);

    }


    @TestFactory
    public Stream<DynamicTest> testShellCommandsForEMRClusterTest() {
        return generateDynamicShellTestCases(COMPONENT, EMR_TESTCASES, envVars);
    }

    @AfterAll
    public static void shutDown() {
        try {
            if (cftClient != null) {
                cftClient.deleteStack();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
