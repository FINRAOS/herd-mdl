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
package org.tsi.mdlt.aws;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.stream.Collectors;

import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.OnFailure;
import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.ResourceStatus;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder;
import com.amazonaws.services.elasticmapreduce.model.ClusterState;
import com.amazonaws.services.elasticmapreduce.model.ClusterSummary;
import com.amazonaws.services.elasticmapreduce.model.ListClustersRequest;
import com.amazonaws.services.elasticmapreduce.model.ListClustersResult;
import com.amazonaws.services.elasticmapreduce.model.TerminateJobFlowsRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.util.TagsReader;
import org.tsi.mdlt.util.TestProperties;

public class CloudFormationClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private AmazonCloudFormation amazonCloudFormation;
    private Properties propertyValues;
    private String masterCFTLocation;
    private String stackName;

    /**
     * Default constructor
     *
     * @param stackSetName - stack name
     */
    public CloudFormationClient(String stackSetName) throws Exception {

        this.stackName = stackSetName;
        propertyValues = TestProperties.getProperties();
        // Create AWS client
        amazonCloudFormation = AmazonCloudFormationClientBuilder.standard()
                .withRegion(Regions.getCurrentRegion().getName())
                .withCredentials(new InstanceProfileCredentialsProvider(true)).build();
    }

    /**
     * Create stack and wait for stack completion(COMPLETED/FAILED)
     *
     * @param params                   stack parameters
     * @param cftTemplateName          cft json file name
     * @param rollbackOnFailure        whether rollback on stack creation failure
     */
    public void createStack(Map<String, String> params, String cftTemplateName, boolean rollbackOnFailure) throws Exception {
        String s3BucketURLPrefix = "https://s3.amazonaws.com/";
        String accountId = AWSSecurityTokenServiceClientBuilder.standard().withRegion(Regions.getCurrentRegion().getName())
            .withCredentials(new InstanceProfileCredentialsProvider(true)).build()
            .getCallerIdentity(new GetCallerIdentityRequest())
            .getAccount();
        masterCFTLocation = s3BucketURLPrefix
                .concat(propertyValues.getProperty("MdltBucketName") + "/")
                .concat("cft" + "/")
                .concat(cftTemplateName);
        System.out.println("Using master CFT location : " + masterCFTLocation);

        CreateStackRequest createStackRequest = new CreateStackRequest();
        createStackRequest.setStackName(stackName);
        createStackRequest.setTemplateURL(masterCFTLocation);
        Collection<String> capabilities = new ArrayList<>();
        capabilities.add("CAPABILITY_NAMED_IAM");
        createStackRequest.setCapabilities(capabilities);

        // Add parameters
        if (!params.isEmpty()) {
            List<Parameter> cftParams = new ArrayList<>();
            Parameter cftParam;
            for (Entry<String, String> param : params.entrySet()) {
                cftParam = new Parameter();
                cftParam.setParameterKey(param.getKey());
                cftParam.setParameterValue(param.getValue());
                cftParams.add(cftParam);
            }
            createStackRequest.setParameters(cftParams);
        }
        //disable rollbackOnFailure on test steps for debugging issues.
        if (!rollbackOnFailure) {
            createStackRequest.setOnFailure(OnFailure.DO_NOTHING);
        }
        createStackRequest.setTags(TagsReader.getStackTags());

        amazonCloudFormation.createStack(createStackRequest);
        LOGGER.info("Stack creation initiated");

        String rootStackId = getStackInfo().stackId(); // Use the stack id to track the create operation
        LOGGER.info("rootStackId   =   " + rootStackId);

        CFTStackStatus cftStackStatus = waitForCompletionAndGetStackStatus(amazonCloudFormation,
                rootStackId);
        LOGGER.info(String
                .format("Stack %s creation completed with status: %s and reasons %s", stackName,
                        cftStackStatus.stackStatus,
                        cftStackStatus.stackReason));
        // Throw exception if failed
        if (!cftStackStatus.getStackStatus().equals(StackStatus.CREATE_COMPLETE.toString())) {
            throw new Exception(
                    "createStack operation failed for stack " + stackName + " - " + cftStackStatus);
        }

    }

    /**
     * Delete the stack {@link #stackName}
     */
    public void deleteStack() throws Exception {

        CFTStackInfo cftStackInfo = getStackInfo();
        String rootStackId = cftStackInfo.stackId(); // Use the stack id to track the delete operation
        LOGGER.info("rootStackId   =   " + rootStackId);

        // Go through the stack and pick up resources that we want
        // to finalize before deleting the stack.
        List<String> s3BucketIds = new ArrayList<>();

        DescribeStacksResult describeStacksResult = amazonCloudFormation.describeStacks();
        for (Stack currentStack : describeStacksResult.getStacks()) {
            if (rootStackId.equals(currentStack.getRootId()) || rootStackId
                    .equals(currentStack.getStackId())) {
                LOGGER.info("stackId   =   " + currentStack.getStackId());
                DescribeStackResourcesRequest describeStackResourcesRequest = new DescribeStackResourcesRequest();
                describeStackResourcesRequest.setStackName(currentStack.getStackName());
                List<StackResource> stackResources = amazonCloudFormation
                        .describeStackResources(describeStackResourcesRequest).getStackResources();
                for (StackResource stackResource : stackResources) {
                    if (!stackResource.getResourceStatus()
                            .equals(ResourceStatus.DELETE_COMPLETE.toString())) {
                        if (stackResource.getResourceType().equals("AWS::S3::Bucket")) {
                            s3BucketIds.add(stackResource.getPhysicalResourceId());
                        }
                    }
                }
            }
        }

        // Now empty S3 buckets, clean up will be done when the stack is deleted
        AmazonS3 amazonS3 = AmazonS3ClientBuilder.standard().withRegion(Regions.getCurrentRegion().getName())
                .withCredentials(new InstanceProfileCredentialsProvider(true)).build();
        for (String s3BucketPhysicalId : s3BucketIds) {
            String s3BucketName = s3BucketPhysicalId;
            LOGGER.info("Empyting S3 bucket, " + s3BucketName);
            ObjectListing objectListing = amazonS3.listObjects(s3BucketName);
            while (true) {
                for (Iterator<?> iterator = objectListing.getObjectSummaries().iterator(); iterator
                        .hasNext(); ) {
                    S3ObjectSummary summary = (S3ObjectSummary) iterator.next();
                    amazonS3.deleteObject(s3BucketName, summary.getKey());
                }
                if (objectListing.isTruncated()) {
                    objectListing = amazonS3.listNextBatchOfObjects(objectListing);
                }
                else {
                    break;
                }
            }
        }

        //Proceed with the regular stack deletion operation
        DeleteStackRequest deleteRequest = new DeleteStackRequest();
        deleteRequest.setStackName(stackName);
        amazonCloudFormation.deleteStack(deleteRequest);
        LOGGER.info("Stack deletion initiated");

        CFTStackStatus cftStackStatus = waitForCompletionAndGetStackStatus(amazonCloudFormation,
                rootStackId);
        LOGGER.info(
                "Stack deletion completed, the stack " + stackName + " completed with " + cftStackStatus);

        // Throw exception if failed
        if (!cftStackStatus.getStackStatus().equals(StackStatus.DELETE_COMPLETE.toString())) {
            throw new Exception(
                    "deleteStack operation failed for stack " + stackName + " - " + cftStackStatus);
        }
    }

    /**
     * Get outputs for the wrapper stack and all nested stacks
     *
     * @return Outputs for stack {@link #stackName}
     */

    public Map<String, String> getStackOutput() throws Exception {

        String rootStackId = getStackInfo().stackId();
        LOGGER.info("rootStackId   =   " + rootStackId);
        Map<String, String> outputs = new HashMap<>();
        outputs.put("rootStackId", rootStackId);

        DescribeStacksResult describeStacksResult = amazonCloudFormation.describeStacks();
        for (Stack currentStack : describeStacksResult.getStacks()) {
            // Get the output details for the running stacks that got created
            // go through both root and nested stacks
            if (currentStack.getStackStatus().equals(StackStatus.CREATE_COMPLETE.toString())
                    && (rootStackId.equals(currentStack.getRootId())
                    || rootStackId.equals(currentStack.getStackId()))) {
                for (Output output : currentStack.getOutputs()) {
                    LOGGER.info(output.getOutputKey() + "   =   " + output.getOutputValue());
                    outputs.put(output.getOutputKey(), output.getOutputValue());
                }
            }
        }
        return outputs;
    }

        public CFTStackStatus waitForCompletionAndGetStackStatus(AmazonCloudFormation awsCloudFormation,
            String stackId) throws InterruptedException {

        int stackStatusPollingInterval =
                Integer.valueOf(propertyValues.getProperty("StackStatusPollingInterval"))
                        * 1000;

        DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
        describeStacksRequest.setStackName(stackId);

        Boolean done = false;
        String stackStatus = "UNKNOWN";
        String stackReason = "";

        System.out.print("Waiting");

        while (!done) {
            List<Stack> stacks = awsCloudFormation.describeStacks(describeStacksRequest).getStacks();
            if (stacks.isEmpty()) {
                done = true;
                stackStatus = StackStatus.DELETE_COMPLETE.toString();
                stackReason = "Stack has been deleted";
            }
            else {
                for (Stack stack : stacks) {
                    if (stack.getStackStatus().equals(StackStatus.CREATE_COMPLETE.toString())
                            || stack.getStackStatus().equals(StackStatus.CREATE_FAILED.toString())
                            || stack.getStackStatus().equals(StackStatus.ROLLBACK_FAILED.toString())
                            || stack.getStackStatus().equals(StackStatus.ROLLBACK_COMPLETE.toString())
                            || stack.getStackStatus().equals(StackStatus.DELETE_COMPLETE.toString())
                            || stack.getStackStatus().equals(StackStatus.DELETE_FAILED.toString())) {
                        done = true;
                        stackStatus = stack.getStackStatus();
                        stackReason = stack.getStackStatusReason();
                    }
                }
            }
            System.out.print(".");
            if (!done) {
                Thread.sleep(stackStatusPollingInterval);
            }
        }
        LOGGER.info("done");

        CFTStackStatus cftStackStatus = new CFTStackStatus(stackStatus, stackReason);
        return cftStackStatus;
    }

    public CFTStackInfo getStackInfo() throws Exception {
        DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
        describeStacksRequest.setStackName(stackName);

        List<Stack> stacks = amazonCloudFormation.describeStacks(describeStacksRequest).getStacks();
        CFTStackInfo cftStackInfo = null;
        if (!stacks.isEmpty()) {
            for (Stack stack : stacks) {
                cftStackInfo = new CFTStackInfo(stack);
            }
        }
        else {
            throw new Exception("Stack not found " + stackName);
        }
        return cftStackInfo;
    }

    public boolean stackExists(String stackName){
        LOGGER.info("Check if stack exists or not :" + stackName);
        DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
        describeStacksRequest.setStackName(stackName);
        try {
            List<Stack> stacks = amazonCloudFormation.describeStacks(describeStacksRequest).getStacks();
        } catch (AmazonCloudFormationException e) {
            if (e.getErrorCode().equals("ValidationError")){
                return false;
            }
        }
        return true;
    }

    public Stack getStackByNamePrefix(String stackNamePrefix) {
        List<Stack> stacksList = amazonCloudFormation.describeStacks().getStacks()
                .stream().filter(stack -> stack.getStackName().startsWith(stackNamePrefix))
                .collect(Collectors.toList());
        assertNotNull(stacksList);
        assertTrue(stacksList.size() > 0);
        return stacksList.get(0);
    }

    public Stack getStackByName(String stackName) {
        DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
        describeStacksRequest.setStackName(stackName);
        List<Stack> stacksList = amazonCloudFormation.describeStacks(describeStacksRequest).getStacks();
        assertNotNull("stack list cannot be null", stacksList);
        assertEquals("only 1 stack is expected, but getting " + stacksList.size(), 1,
                stacksList.size());
        return stacksList.get(0);
    }

    public void waitForClusterTermination(AmazonElasticMapReduce amazonElasticMapReduce,
            List<String> stackClusterIds, CFTStackInfo cftStackInfo) throws InterruptedException {
        int stackStatusPollingInterval =
                Integer.valueOf(propertyValues.getProperty("StackStatusPollingInterval"))
                        * 1000;

        System.out.print("Waiting");
        Boolean done = false;
        List<ClusterSummary> clusters = null;

        while (!done) {
            clusters = getStackClustersSummary(amazonElasticMapReduce, stackClusterIds, cftStackInfo);
            if (clusters.isEmpty()) {
                done = true;
            }
            else {
                for (ClusterSummary cluster : clusters) {
                    //LOGGER.info("cluster -> " + cluster.getName() + " - " + cluster.getStatus());
                    if (!cluster.getStatus().getState().equals(ClusterState.TERMINATED.toString()) &&
                            !cluster.getStatus().getState()
                                    .equals(ClusterState.TERMINATED_WITH_ERRORS.toString())) {
                        done = false;
                        continue;
                    }
                    else {
                        done = true;
                    }
                }
            }
            System.out.print(".");
            if (!done) {
                Thread.sleep(stackStatusPollingInterval);
            }
        }
        LOGGER.info("done");

        //Print a summary of the termination status for all the clusters
        if (clusters != null) {
            for (ClusterSummary cluster : clusters) {
                LOGGER.info(
                        "Cluster " + cluster.getName() + " terminated with status " + cluster.getStatus()
                                .getState());
            }
        }
    }

    public List<ClusterSummary> getStackClustersSummary(AmazonElasticMapReduce amazonElasticMapReduce,
            List<String> stackClusterIds, CFTStackInfo cftStackInfo) {
        List<ClusterSummary> stackClustersSummary = new ArrayList<>();
        ListClustersRequest listClustersRequest = new ListClustersRequest();
        //Only get clusters that got created after we setup our stack
        listClustersRequest.setCreatedAfter(cftStackInfo.creationTime());

        ListClustersResult listClustersResult = amazonElasticMapReduce
                .listClusters(listClustersRequest);
        while (true) {
            for (ClusterSummary cluster : listClustersResult.getClusters()) {
                if (stackClusterIds.contains(cluster.getId())) {
                    stackClustersSummary.add(cluster);
                }
            }
            if (listClustersResult.getMarker() != null) {
                listClustersRequest.setMarker(listClustersResult.getMarker());
                listClustersResult = amazonElasticMapReduce.listClusters(listClustersRequest);
            }
            else {
                break;
            }
        }
        return stackClustersSummary;
    }

    public static class CFTStackInfo {

        private Stack stack;

        public CFTStackInfo(Stack stack) {
            this.stack = stack;
        }

        public String stackName() {
            return stack.getStackName();
        }

        public String stackId() {
            return stack.getStackId();
        }

        public List<Output> outputs() {
            return stack.getOutputs();
        }

        public List<Parameter> paramters() {
            return stack.getParameters();
        }

        public Date creationTime() {
            return stack.getCreationTime();
        }
    }

    public static class CFTStackStatus {

        private String stackStatus;
        private String stackReason;

        public CFTStackStatus(String stackStatus, String stackReason) {
            this.stackStatus = stackStatus;
            this.stackReason = stackReason;
        }

        public String getStackStatus() {
            return stackStatus;
        }

        public String getStackReason() {
            return stackReason;
        }

        @Override
        public String toString() {
            return stackStatus + " (" + stackReason + ")";
        }
    }
}
