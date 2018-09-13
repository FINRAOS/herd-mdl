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
package org.tsi.mdlt.test.herd;

import static org.awaitility.Awaitility.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.UnmarshalException;

import com.amazonaws.services.cloudformation.model.AlreadyExistsException;
import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.aws.CloudFormationClient;
import org.tsi.mdlt.aws.SsmUtil;
import org.tsi.mdlt.enums.SsmParameterKeyEnum;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.enums.StackOutputKeyEnum;
import org.tsi.mdlt.pojos.User;
import org.tsi.mdlt.test.BaseTest;
import org.tsi.mdlt.util.FileUtil;
import org.tsi.mdlt.util.HerdRestUtil;
import org.tsi.mdlt.util.StackOutputPropertyReader;
import org.tsi.mdlt.util.TestProperties;
import org.tsi.mdlt.util.shell.ShellCommandProperty;

import org.finra.herd.model.api.xml.EmrCluster;

public class EMRClusterTest extends BaseTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String EMR_PREREQ_STACK_SUFFIX = "EmrPreReqStack";
    private static final String EMR_CLUSTER_PREREQ_CFT = "mdltClusterPrereq.yml";

    private static CloudFormationClient cftClient;
    private static Map<String, String> envVars = ShellCommandProperty.getPropertiesMap();

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

    @Test
    public void testHerdEMRCluster() {
        User herdAdminUser = User.getHerdAdminUser();
        String namespace = "MDLT";
        String clusterDefinitionName = "MDLTTestCluster";
        String clusterName = StackOutputPropertyReader.get(StackOutputKeyEnum.MDL_INSTANCE_NAME) + "_Cluster";

        LogStep("Create Cluster, Definition, and namespace");
        HerdRestUtil.postNamespace(herdAdminUser, namespace);
        HerdRestUtil.createClusterDefinition(herdAdminUser, getCreateClusterDefinitionBody(namespace, clusterDefinitionName));
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.createCluster(herdAdminUser, getCreateClusterBody(namespace, clusterDefinitionName, clusterName)).statusCode());

        LogStep("Wait for cluster to be up");
        int timeout = 10;
        given().atMost(timeout, TimeUnit.MINUTES).pollInterval(30, TimeUnit.SECONDS)
            .ignoreException(UnmarshalException.class)
            .until(() -> {
                LOGGER.info("Polling every 30 seconds with timeout: " + timeout + " minutes");

                EmrCluster emrCluster = HerdRestUtil.getCluster(herdAdminUser, namespace, clusterDefinitionName, clusterName).as(EmrCluster.class);
                return emrCluster.getStatus().equals("WAITING");
            });

        LogStep("Delete Cluster");
        assertEquals(HttpStatus.SC_OK, HerdRestUtil.deleteCluster(herdAdminUser, namespace, clusterDefinitionName, clusterName).statusCode());

        LogStep("Wait for cluster to be deleted");
        given().atMost(timeout, TimeUnit.MINUTES).pollInterval(30, TimeUnit.SECONDS)
            .until(() -> {
                LOGGER.info("Polling every 30 seconds with timeout: " + timeout + " minutes");
                Response response = HerdRestUtil.getCluster(herdAdminUser, namespace, clusterDefinitionName, clusterName);
                return response.statusCode() == HttpStatus.SC_BAD_REQUEST;
            });
    }


    @After
    public void afterTest() {
        cleanup();
    }

    private void cleanup() {
        User herdAdminUser = User.getHerdAdminUser();
        String namespace = "MDLT";
        String clusterDefinitionName = "MDLTTestCluster";

        LogStep("Delete Cluster Definition and namespace");
        HerdRestUtil.deleteClusterDefinition(herdAdminUser, namespace, clusterDefinitionName);
        HerdRestUtil.deleteNamespace(herdAdminUser, namespace);
    }

    @AfterAll
    public static void shutDown() {
        try {
            if (cftClient != null) {
                cftClient.deleteStack();
            }
        }
        catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
    }

    private String getCreateClusterDefinitionBody(String namespace, String clusterDefinitionName) {
        return substituteVariables(FileUtil.readFileFromJar("/xml/herd/cluster/createClusterDefinition.xml"))
            .replace("$namespace", namespace)
            .replace("$clusterDefinitionName", clusterDefinitionName);
    }

    private String getCreateClusterBody(String namespace, String clusterDefinitionName, String clusterName) {
        return substituteVariables(FileUtil.readFileFromJar("/xml/herd/cluster/createCluster.xml"))
            .replace("$namespace", namespace)
            .replace("$clusterDefinitionName", clusterDefinitionName)
            .replace("$clusterName", clusterName);
    }

    private String substituteVariables(String content) {
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            content = content.replaceAll("\\$" + entry.getKey(), entry.getValue());
        }
        return content;
    }
}
