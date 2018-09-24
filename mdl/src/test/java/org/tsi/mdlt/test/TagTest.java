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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTagsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.AmazonRDSClientBuilder;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.ListTagsForResourceRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.aws.CloudFormationClient;
import org.tsi.mdlt.enums.StackOutputKeyEnum;
import org.tsi.mdlt.util.StackOutputPropertyReader;

/**
 * Test class to verify tag related testcases, stack tag propagation, stack tags are copied to sqs
 */
public class TagTest extends BaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String INSTANCE_NAME = StackOutputPropertyReader.get(StackOutputKeyEnum.MDL_INSTANCE_NAME);
    private static final String APP_STACK_NAME = StackOutputPropertyReader.get(StackOutputKeyEnum.APP_STACK_NAME);
    private static final String ES_EC2_IP = StackOutputPropertyReader.get(StackOutputKeyEnum.ES_EC2_IP);

    private static final String EC_2_FILTER_PRIVATE_IP = "private-ip-address";
    private static final String DEPLOY_HOST_SG_SUFFIX = "DeployHostSecurityGroup";

    @Test
    public void testSqsTagsAreSameAsHerdEC2Stack() throws Exception {
        String sqsNamePrefix = INSTANCE_NAME;
        String herdStackNamePrefix = APP_STACK_NAME + "-MdlStack-";

        CloudFormationClient cloudFormationClient = new CloudFormationClient(APP_STACK_NAME);
        List<Tag> stackTags = cloudFormationClient.getStackByNamePrefix(herdStackNamePrefix).getTags();

        System.out.println("Listing all queues with prefix: " + sqsNamePrefix);
        AmazonSQS sqs = AmazonSQSClientBuilder.standard().withRegion(Regions.getCurrentRegion().getName())
            .withCredentials(new InstanceProfileCredentialsProvider(true)).build();
        List<String> queueUrls = sqs.listQueues(sqsNamePrefix).getQueueUrls();
        assertEquals(2, queueUrls.size(), "2 queues are expected");
        for (String queueUrl : queueUrls) {
            System.out.println("QueueUrl: " + queueUrl);
            Map<String, String> sqsTags = sqs.listQueueTags(queueUrl).getTags();

            LogVerification("Verify sqs tags are the same as herd stack");
            stackTags.forEach(tag -> {
                String key = tag.getKey();
                assertTrue(sqsTags.containsKey(key));
                assertEquals(tag.getValue(), sqsTags.get(key));
            });
        }
    }

    @Test
    public void testWrapperStackTagsAreCopiedToStackResources() throws Exception {
        CloudFormationClient cloudFormationClient = new CloudFormationClient(APP_STACK_NAME);
        List<Tag> wrapperStackTags = cloudFormationClient.getStackByName(APP_STACK_NAME).getTags();

        LogVerification("Verify wrapper stack tags are copied to nested Stacks");
        List<Tag> nestedStackTags = getNestedStackTags(APP_STACK_NAME);
        for (Tag tag : wrapperStackTags) {
            assertTrue(nestedStackTags.stream()
                .anyMatch(nestedStackTag -> nestedStackTag.getKey().equals(tag.getKey())
                    && nestedStackTag.getValue().equals(tag.getValue())));
        }

        LogVerification("Verify wrapper stack tags are copied to S3");
        String s3BucketName = StackOutputPropertyReader.get(StackOutputKeyEnum.MDL_STAGING_BUCKET_NAME);
        AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.getCurrentRegion().getName())
            .withCredentials(new InstanceProfileCredentialsProvider(true)).build();
        Map<String, String> s3Tags = s3.getBucketTaggingConfiguration(s3BucketName).getTagSet().getAllTags();

        wrapperStackTags.forEach(tag -> {
            String key = tag.getKey();
            assertTrue(s3Tags.containsKey(key));
            assertEquals(tag.getValue(), s3Tags.get(key));
        });

        LogVerification("Verify wrapper stack tags are copied to RDS");
        List<com.amazonaws.services.rds.model.Tag> rdsTags = getRdsTagsWithPrefix(INSTANCE_NAME);
        for (Tag tag : wrapperStackTags) {
            assertTrue(rdsTags.stream().anyMatch(rdsTag -> rdsTag.getKey().equals(tag.getKey())
                && rdsTag.getValue().equals(tag.getValue())));
        }

        LogVerification("Verify wrapper stack tags are copied to EC2");
        List<com.amazonaws.services.ec2.model.Tag> ec2Tags = getEc2Tags(ES_EC2_IP);
        for (Tag tag : wrapperStackTags) {
            assertTrue(ec2Tags.stream().anyMatch(ec2Tag -> ec2Tag.getKey().equals(tag.getKey())
                && ec2Tag.getValue().equals(tag.getValue())));
        }

        LogVerification("Verify wrapper stack tags are copied to Security Group");
        List<com.amazonaws.services.ec2.model.Tag> securityGroupTags = getSecurityGroupTagsWithPrefix(INSTANCE_NAME);
        for (Tag tag : wrapperStackTags) {
            assertTrue(securityGroupTags.stream()
                .anyMatch(securityGroupTag -> securityGroupTag.getKey().equals(tag.getKey())
                    && securityGroupTag.getValue().equals(tag.getValue())));
        }

        LogVerification("Verify wrapper stack tags are copied to Elastic Load Balancer");
        List<com.amazonaws.services.elasticloadbalancingv2.model.Tag> elbTags = getElbTags();
        for (Tag tag : wrapperStackTags) {
            assertTrue(elbTags.stream().anyMatch(elbTag -> elbTag.getKey().equals(tag.getKey())
                && elbTag.getValue().equals(tag.getValue())));
        }
    }

    private List<com.amazonaws.services.rds.model.Tag> getRdsTagsWithPrefix(String rdsIdentifierPrefix) {
        assertNotNull(rdsIdentifierPrefix);

        AmazonRDS rds = AmazonRDSClientBuilder.standard().withRegion(Regions.getCurrentRegion().getName())
            .withCredentials(new InstanceProfileCredentialsProvider(true)).build();
        List<DBInstance> dbInstances = rds.describeDBInstances().getDBInstances().stream()
            .filter(dbInstance -> dbInstance.getDBInstanceIdentifier().startsWith(rdsIdentifierPrefix))
            .collect(Collectors.toList());
        assertNotNull(dbInstances);
        assertTrue(dbInstances.size() > 0);
        String herdRdsARN = dbInstances.get(0).getDBInstanceArn();
        LOGGER.info("Getting RDS: " + herdRdsARN);

        ListTagsForResourceRequest rdsRequest = new ListTagsForResourceRequest().withResourceName(herdRdsARN);
        return rds.listTagsForResource(rdsRequest).getTagList();
    }

    private List<com.amazonaws.services.ec2.model.Tag> getEc2Tags(String privateEc2Ip) {
        assertNotNull(privateEc2Ip);

        AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(Regions.getCurrentRegion().getName())
            .withCredentials(new InstanceProfileCredentialsProvider(true)).build();
        DescribeInstancesRequest ec2Request = new DescribeInstancesRequest().withFilters(new Filter(EC_2_FILTER_PRIVATE_IP).withValues(privateEc2Ip));
        List<Reservation> reservations = ec2.describeInstances(ec2Request).getReservations();
        assert reservations != null && reservations.size() > 0;
        List<Instance> instances = ec2.describeInstances(ec2Request).getReservations().get(0).getInstances();
        assert instances != null && instances.size() > 0;

        LOGGER.info("Getting EC2: " + instances.get(0).getInstanceId());
        return instances.get(0).getTags();
    }

    private List<com.amazonaws.services.ec2.model.Tag> getSecurityGroupTagsWithPrefix(String sgNamePrefix) {
        assertNotNull(sgNamePrefix);

        AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(Regions.getCurrentRegion().getName())
            .withCredentials(new InstanceProfileCredentialsProvider(true)).build();
        List<SecurityGroup> securityGroups = ec2.describeSecurityGroups().getSecurityGroups().stream()
            //exclude security group created by DeployHostStack, as DeployHostStack doesn't have tag attached
            .filter(sg -> sg.getGroupName().startsWith(sgNamePrefix) && !sg.getGroupName().trim().endsWith(DEPLOY_HOST_SG_SUFFIX))
            .collect(Collectors.toList());
        assert securityGroups != null && securityGroups.size() > 0;

        LOGGER.info("Getting SG: " + securityGroups.get(0).getGroupName());
        return securityGroups.get(0).getTags();
    }

    private List<Tag> getNestedStackTags(String wrapperStackName) throws Exception {
        String nestedStackNamePrefix = wrapperStackName + "-";
        CloudFormationClient cloudFormationClient = new CloudFormationClient(wrapperStackName);
        return cloudFormationClient.getStackByNamePrefix(nestedStackNamePrefix).getTags();
    }

    private List<com.amazonaws.services.elasticloadbalancingv2.model.Tag> getElbTags() {
        String elbArn = getAnyElbArn();

        AmazonElasticLoadBalancing client = AmazonElasticLoadBalancingClientBuilder.standard()
            .withRegion(Regions.getCurrentRegion().getName()).withCredentials(new InstanceProfileCredentialsProvider(true))
            .build();
        DescribeTagsRequest request = new DescribeTagsRequest().withResourceArns(elbArn);
        return client.describeTags(request).getTagDescriptions().get(0).getTags();
    }

    private String getAnyElbArn() {
        AmazonElasticLoadBalancing amazonElasticLoadBalancing = AmazonElasticLoadBalancingClientBuilder
            .standard()
            .withRegion(Regions.getCurrentRegion().getName()).withCredentials(new InstanceProfileCredentialsProvider(true))
            .build();
        String bdsqlLbArn = StackOutputPropertyReader.get(StackOutputKeyEnum.BDSQL_LB_ARN);
        List<LoadBalancer> elbs = new ArrayList<>(
            amazonElasticLoadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest().withLoadBalancerArns(bdsqlLbArn))
                .getLoadBalancers());
        assert elbs != null && elbs.size() > 0;

        LOGGER.info("Getting ELB: " + elbs.get(0).getLoadBalancerName());
        return elbs.get(0).getLoadBalancerArn();
    }
}
