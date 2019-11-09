Herd-MDL Advanced Install
============================

Use these Advanced Installation instructions if your organization requires that certain AWS resources such as IAM Roles, Security Groups, etc. are created outside Herd-MDL automated install. The Advanced Install allows for optional creation of AWS resources through other mechanisms and provides detailed specifications on what to create and how to reference resources created outside Herd-MDL automated install.

See [Basic Install](basic-install.md) for an easy, turnkey installation of Herd-MDL. 

## Prerequisites

These are prerequisites that are necessary for installing MDL components for Advanced Installation Type

*   An [AWS](https://aws.amazon.com) account 
*   User who has power user access as per this policy - [arn:aws:iam::aws:policy/PowerUserAccess](https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies_job-functions.html#jf_developer-power-user)
    *   MDL deployment creates various AWS resources like Cloudformation, EC2, IAM, Security Groups, S3 etc, and power user access is needed for creating these resources
    *   Sample IAM policy for [PowerUserAccess](attachments/1070290969/1070291504.txt)
*   Domain - if (EnableSSLAndAuth == true)
    *   User account needs to own a Domain for using with Route53 record set for public end points created by MDL
    *   Refer [AWS documentation](https://docs.aws.amazon.com/acm/latest/userguide/setup-domain.html) for setting up a new Domain
    *   Domain must be owned by User in case "EnableSSLAndAuth" parameter is specified
*   Certificate in ACM with [Wildcard Domain Name](https://aws.amazon.com/certificate-manager/faqs/#acm-certificates) - if (EnableSSLAndAuth == true)
    *   The Certificate should match any first level subdomain (Wildcard character supports this)
    *   Format - ***.domain**
        *   Example: ***.example.com**
    *   MDL prefixes corresponding first level subdomain (Example: **mdlHerd.example.com**, **mdlShepherd.example.com**, and **mdlBdsql.example.com**)
    *   Certificate in ACM needs to exist in the AWS account in case "EnableSSLAndAuth" parameter is specified
*   Hosted Zone in Route53 - if (EnableSSLAndAuth == true)
    *   MDL adds a record set for Hosted Zone in Route53 to associate the DNS information with Domain Name
    *   MDL creates three record sets (Example: **mdlHerd.example.com**, **mdlShepherd.example.com**, and **mdlBdsql.example.com**)
    *   Hosted Zone needs to exist in the AWS account in case "EnableSSLAndAuth" parameter is specified
*   There are some conditional parameters for MDL, which let the user to use existing resources instead of creating them as part of MDL. In these case, corresponding SSM parameters need to be created before creating MDL stack. Following are the conditional resource types in MDL.
    *   VPC/Subnets - if (CreateVPC == false)
        *   Refer to [CreateVPC section](#conditional-parameters) and create SSM parameters in case existing VPC/Subnets need to be used
    *   S3 - if (CreateS3Buckets == false)
        *   Refer  to [CreateS3Buckets section](#conditional-parameters) and create SSM parameters in case existing S3 buckets need to be used
    *   IAM - if (CreateIAMRoles == false)
        *   Refer to [CreateIAMRoles section](#conditional-parameters) and create SSM parameters in case existing IAM roles need to be used
    *   RDS - if (CreateRDSInstances == false)
        *   Refer to [CreateRDSInstances section](#conditional-parameters) and create SSM parameters in case existing RDS instances need to be used
    *   Security Groups - if (CreateSecurityGroups == false)
        *   Refer to [CreateSecurityGroups section](#conditional-parameters) and create SSM parameters in case existing Security Groups need to be used
    *   SQS - if (CreateSQS == false)
        *   Refer to [CreateSQS section](#conditional-parameters) and create SSM parameters in case existing SQS need to be used
    *   OpenLDAP - if (CreateOpenLDAP  == false)
        *   Refer to [CreateOpenLDAP section](#conditional-parameters) and create SSM parameters in case existing OpenLDAP need to be used
    *   KeyPair - if (CreateKeypair == false)
        *   Refer to [CreateKeypair section](#conditional-parameters) and create SSM parameters in case existing KeyPair needs to be used
    *   Cloudfront Distribution - if (CreateCloudFrontDistribution == false)
        *   Refer to [ CreateCloudFrontDistribution section](#conditional-parameters) and create SSM parameters in case existing Cloudfront needs to be used

## Steps

Installation is automated through Cloudformation templates in AWS. The stack creates all the resources required by MDL application. This roughly takes a couple of hours to create all the resources needed for MDL. A stack can be created using AWS console, or AWS CLI, or AWS SDK. Refer [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/stacks.html) for creating stacks using Cloudformation templates. In this section, steps are described for creating the stack using AWS console.

*   Download the attached [installMDL.yml](https://github.com/FINRAOS/herd-mdl/releases/download/mdl-v1.5.0/installMDL.yml) file to local file system
*   Login to AWS console and navigate to [Cloudformation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-console-login.html)
*   Create the stack using option "Upload a template to Amazon S3" - Refer [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-template.html) for selecting a local template
*   Choose the installMDL.yml file from local file system
*   In the next page, 
    *   Enter the values for [Stack Name](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-parameters.html)  
        *   A stack name can contain only alphanumeric characters (case-sensitive) and hyphens. It must start with an alphabetic character and can't be longer than 128 characters.
    *   Refer to [MDL CFT Specifications](#conditional-parameters) and change the required parameters for the chosen installation type  
        
*   In the next page, specify the stack options as per [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-console-add-tags.html)
*   Review the parameters, and create the stack as per [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-review.html)
*   Wait for "CREATE_COMPLETE" on the stack and all nested stacks.

## CFT Specifications


### Deployment Parameters

These parameters are related to which version and components to deploy.

**ReleaseVersion**

|   |   |
| ----- | ----- |
| **Name** | ReleaseVersion |
| **Description** | Release version of MDL application to install |
| **Required** | Yes |
| **Default Value** | 1.4.0 (latest release) |
| **Allowed Values** | 1.0.0, 1.1.0, 1.2.0, 1.3.0, 1.4.0 |

**DeployComponents**

|   |   |
| ----- | ----- |
| **Name** | DeployComponents |
| **Description** | MDL Component to deploy |
| **Required** | Yes |
| **Default Value** | All |
| **Allowed Values** | <table><tr><td>**Parameter Value**</td><td>**Description**</td></tr><tr><td>All</td><td>Install all the available Components</td></tr><tr><td>Prereqs Only</td><td>Install only the pre-requirements without any EC2</td></tr><tr><td>Herd</td><td>Install Prereqs then Herd and its dependencies</td></tr><tr><td>Metastor</td><td>Install Prereqs, Herd, Metastor and all their depencencies</td></tr></table> |

### Generic Parameters

These parameters define the basic parameters used across various components

**ImageId** 

|   |   |
| ----- | ----- |
| **Name** | ImageId |
| **Description** | AMI id for the EC2 instances. Note that OSS user may use any other AMI which is similar to amzn-ami-hvm-2017.09.1.20180307-x86_64-gp2. However, there could be some issues in terms of package installation/availability, while using a different AMI. So, it is user's responsibility to make sure provided AMI has all the packages like amzn-ami-hvm-2017.09.1.20180307-x86_64-gp2 |
| **Required** | Yes |
| **Default Value** | ami-1853ac65 |

**MDLInstanceName**

|   |   |
| ----- | ----- |
| **Name** | MDLInstanceName |
| **Description** | Name of the Application being installed |
| **Required** | Yes |
| **Default Value** | mdl |
| **Allowed Pattern** | \[a-z0-9_\]* |
| **Max Length** | 15 |

**Environment**

|   |   |
| ----- | ----- |
| **Name** | Environment |
| **Description** | Environment name for MDL |
| **Required** | Yes |
| **Default Value** | prod |
| **Allowed Pattern** | \[a-z0-9_\]* |
| **Max Length** | 4 |

**CloudWatchRetentionDays**

|   |   |
| ----- | ----- |
| **Name** | CloudWatchRetentionDays |
| **Description** | Retention days for CloudWatch logs |
| **Required** | Yes |
| **Default Value** | 90 |

### Conditional Parameters

These are conditional parameters to decide whether MDL creates certain resources or MDL uses existing resources. In each case where a parameter is false, SSM parameters must be present that allow MDL to reference the resources that have been created prior to running the Herd-MDL automated install. 

**CreateS3Buckets**

|   |   |
| ----- | ----- |
| **Name** | CreateS3Buckets |
| **Description** | Specifies whether to create S3 buckets or to use existing s3 buckets. User needs to fill the SSM parameters as per below information in case of using existing s3 buckets. |
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |
| **SSM Parameters** (required if false) | <table><tr><td>**Description**</td><td>**Name (Format)**</td><td>**Name (Example)**</td><td>**Value (Format)**</td><td>**Value (Example)**</td></tr><tr><td>Herd Bucket</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/S3/Herd</td><td>/app/MDL/mdl/prod/S3/Herd</td><td>{{BucketName}}</td><td>123456789012-mdl-herd-prod</td></tr><tr><td>MDL Bucket</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/S3/MDL</td><td>app/MDL/mdl/prod/S3/MDL</td><td>{{BucketName}}</td><td>123456789012-mdl-mdl-prod </td></tr><tr><td>Shepherd Bucket</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/S3/Shepherd</td><td>app/MDL/mdl/prod/S3/Shepherd</td><td>{{BucketName}}</td><td>123456789012-mdl-shepherd-prod </td></tr></table> |

**CreateIAMRoles**   
 
|   |   |
| ----- | ----- |
| **Name** | CreateIAMRoles |
| **Description** | Specifies whether to create IAM roles or to use existing IAM roles. User needs to fill the SSM parameters as per below information in case of using existing IAM roles. |
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |
| **SSM Parameters** (required if false) | <table><tr><td>**Description**</td><td>**Name (Format)**</td><td>**Name (Example)**</td><td>**Value (Format)**</td><td>**Value (Example)**</td></tr><tr><td>EMR Service Role <br>[Permissions Required](images/APP_mdl_EMR.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/IAM/MDLEMRServiceRole</td><td>/app/MDL/mdl/prod/IAM/MDLEMRServiceRole</td><td>{{EMRRoleName}}</td><td>APP_mdl_EMR</td></tr><tr><td>MDL Instance Profile for EC2instances<br>[Permissions Required](images/mdl-MDLInstanceProfile.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/IAM/MDLInstanceProfile</td><td>/app/MDL/mdl/prod/IAM/MDLInstanceProfile</td><td>{{MDLInstanceProfileName}}</td><td>mdl-MDLInstanceProfile</td></tr><tr><td>MDL Instance Role for EC2 instances<br>[Permissions Required](images/mdl-MDLInstanceProfile.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/IAM/MDLInstanceRole</td><td>/app/MDL/mdl/prod/IAM/MDLInstanceRole</td><td>{{MDLInstanceProfileARN}}</td><td>arn:aws:iam::123456789012:role/APP_mdl_Instance</td></tr><tr><td>Code Deployment Role for EC2<br>[Permissions Required](images/APP_mdl_Deployment.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/IAM/MDLServerDeploymentRole</td><td>/app/MDL/mdl/prod/IAM/MDLServerDeploymentRole</td><td>{{MDLServerDeploymentRoleARN}}</td><td>arn:aws:iam::123456789012:role/APP_mdl_Deployment</td></tr></table> |
  
**CreateRDSInstances**  

|   |   |
| ----- | ----- |
| **Name** | CreateRDSInstances |
| **Description** | Specifies whether to create RDS or to use existing RDS. User needs to fill the SSM parameters as per below information in case of using existing RDS. |
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |
| **SSM Parameters** (required if false) | <table><tr><td>**Description**</td><td>**Name (Format)**</td><td>**Name (Example)**</td><td>**Value (Format)**</td><td>**Value (Example)**</td></tr><tr><td>Herd RDS DB Host Name</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/RDS/HerdDBHostName</td><td>/app/MDL/mdl/prod/RDS/HerdDBHostName</td><td>{{DBHostName}}</td><td>mdl-prod-herd.ctrfbf70ykmy.us-east-1.rds.amazonaws.com</td></tr><tr><td>Metastor RDS DB HostName</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/RDS/MetastorDBHostName</td><td>/app/MDL/mdl/prod/RDS/MetastorDBHostName</td><td>{{DBHostName}}</td><td>mdl-prod-metastor.ctrfbf70ykmy.us-east-1.rds.amazonaws.com</td></tr><tr><td>Password for Herd RDS Master Account</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/HERD/RDS/masterAccount</td><td>/app/MDL/mdl/dev/HERD/RDS/masterAccount</td><td>{{Password}}-This is a Secure String</td><td>wbPAEl55nd2lF2H5</td></tr><tr><td>Password for Metastor RDS Master Account</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/METASTOR/RDS/hiveAccount</td><td>/app/MDL/mdl/dev/METASTOR/RDS/hiveAccount</td><td>{{Password}}-This is a Secure String</td><td>wvFST98TFO7uCwtR</td></tr></table> |  

**CreateSecurityGroups** 

|   |   |
| ----- | ----- |
| **Name** | CreateSecurityGroups |
| **Description** | Specifies whether to create Security Groups or to use existing Security Groups. User needs to fill the SSM parameters as per below information in case of using existing Security Groups. |
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |
| **SSM Parameters** (required if false) | <table><tr><td>**Description**</td><td>**Name (Format)**</td><td>**Name (Example)**</td><td>**Value (Format)**</td><td>**Value (Example)**</td></tr><tr><td>Security Group for BDSQL ALB<br>[Ports Required](images/BdsqlALBSecurityGroup.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SecurityGroup/BdsqlALB</td><td>/app/MDL/mdl/prod/SecurityGroup/BdsqlALB</td><td>{{SecurityGroupName}}</td><td>mdl-prod-BdsqlALBSecurityGroup</td></tr><tr><td>Security Group for BDSQL EMR Service<br>[Ports Required](images/BdsqlEMRServiceSecurityGroup.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SecurityGroup/BdsqlEMRService</td><td>/app/MDL/mdl/prod/SecurityGroup/BdsqlEMRService</td><td>{{SecurityGroupName}}</td><td>mdl-prod-BdsqlEMRServiceSecurityGroup</td></tr><tr><td>Security Group for BDSQL EMR Slave Nodes<br>[Ports Required](images/BdsqlEMRSlaveSecurityGroup.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SecurityGroup/BdsqlEMRSlave</td><td>/app/MDL/mdl/prod/SecurityGroup/BdsqlEMRSlave</td><td>{{SecurityGroupName}}</td><td>mdl-prod-BdsqlEMRSlaveSecurityGroup</td></tr><tr><td>Security Group for Elastic Search EC2<br>[Ports Required](images/ElasticSearchSecurityGroup.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SecurityGroup/ElasticSearch</td><td>/app/MDL/mdl/prod/SecurityGroup/ElasticSearch</td><td>{{SecurityGroupName}}</td><td>mdl-prod-ElasticSearchSecurityGroup</td></tr><tr><td>Security Group for Herd EC2-<br>[Ports Required](images/HerdSecurityGroup.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SecurityGroup/Herd</td><td>/app/MDL/mdl/prod/SecurityGroup/Herd</td><td>{{SecurityGroupName}}</td><td>mdl-prod-HerdSecurityGroup</td></tr><tr><td>Security Group for Herd ALB<br>[Ports Required](images/HerdALBSecurityGroup.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SecurityGroup/HerdALB</td><td>/app/MDL/mdl/prod/SecurityGroup/HerdALB</td><td>{{SecurityGroupName}}</td><td>mdl-prod-HerdALBSecurityGroup</td></tr><tr><td>Security Group for Herd RDS<br>[Ports Required](images/HerdRDSSecurityGroup.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SecurityGroup/HerdRDS</td><td>/app/MDL/mdl/prod/SecurityGroup/HerdRDS</td><td>{{SecurityGroupName}}</td><td>mdl-prod-HerdRDSSecurityGroup</td></tr><tr><td>Security Group for Metastor EC2<br>[Ports Required](images/MetastorSecurityGroup.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SecurityGroup/Metastor</td><td>/app/MDL/mdl/prod/SecurityGroup/Metastor</td><td>{{SecurityGroupName}}</td><td>mdl-prod-MetastorSecurityGroup</td></tr><tr><td>Security Group for Metastor EMR<br>[Ports Required](images/MetastorEMRSecurityGroup.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SecurityGroup/MetastorEMR</td><td>/app/MDL/mdl/prod/SecurityGroup/MetastorEMR</td><td>{{SecurityGroupName}}</td><td>mdl-prod-MetastorEMRSecurityGroup</td></tr><tr><td>Security Group for Metastor RDS<br>[Ports Required](images/MetastorRDSSecurityGroup.json)</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SecurityGroup/MetastorRDS</td><td>/app/MDL/mdl/prod/SecurityGroup/MetastorRDS</td><td>{{SecurityGroupName}}</td><td>mdl-prod-MetastorRDSSecurityGroup</td></tr></table> |

**CreateSQS** 

|   |   |
| ----- | ----- |
| **Name** | CreateSQS |
| **Description** | Specifies whether to create SQS or to use existing SQS. User needs to fill the SSM parameters as per below information in case of using existing SQS. |
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |
| **SSM Parameters** (required if false) | <table><tr><td>**Description**</td><td>**Name (Format)**</td><td>**Name (Example)**</td><td>**Value (Format)**</td><td>**Value (Example)**</td></tr><tr><td>Flag about whether SQS was created by CFT</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SQS/CreatedByMDL</td><td>/app/MDL/mdl/prod/SQS/CreatedByMDL</td><td>false</td><td>false</td></tr><tr><td>Herd SQS Name</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SQS/HerdQueueIn</td><td>/app/MDL/mdl/prod/SQS/HerdQueueIn</td><td>{{SQSName}}</td><td>mdl-prod-HERD-INCOMING</td></tr><tr><td>ES Index SQS</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/SQS/SearchIndexUpdateSqsQueue</td><td>/app/MDL/mdl/prod/SQS/SearchIndexUpdateSqsQueue</td><td>{{SQSName}}</td><td>mdl-prod-ESEARCH-SEARCH_INDEX_UPDATE</td></tr></table> | 

**CreateOpenLDAP** 

|   |   |
| ----- | ----- |
| **Name** | CreateOpenLDAP |
| **Description** | Specifies whether to create OpenLDAP Server or to use existing OpenLDAP Server. User needs to fill the SSM parameters as per below information in case of using existing OpenLDAP Server for Authentication. |
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |
| **SSM Parameters** (required if false) | <table><tr><td>**Description**</td><td>**Name (Format)**</td><td>**Name (Example)**</td><td>**Value (Format)**</td><td>**Value (Example)**</td></tr><tr><td>VPCId</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/VPC/ID</td><td>/global/mdl/prod/VPC/ID</td><td>{{VPCID}}</td><td>vpc-abc01234</td></tr><tr><td>Private Subnet List</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/VPC/SubnetIDs/private</td><td>/global/mdl/prod/VPC/SubnetIDs/private</td><td>{{PrivateSubnetsList}}</td><td>subnet-abc01234,subnet-abc56789</td></tr><tr><td>Public Subnet List</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/VPC/SubnetIDs/public</td><td>/global/mdl/prod/VPC/SubnetIDs/public</td><td>{{PublicSubnetsList}}</td><td>subnet-abc01234,subnet-abc56789</td></tr></table> |
  
**CreateVPC** 

|   |   |
| ----- | ----- |
| **Name** | CreateVPC |
| **Description** | Specifies whether to create new VPC/Subnets or to use existing VPC/Subnets. User needs to fill the SSM parameters as per below information in case of using existing VPC/Subnets. |
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |
| **SSM Parameters** (required if false) | <table><tr><td>**Description**</td><td>**Name (Format)**</td><td>**Name (Example)**</td><td>**Value (Format)**</td><td>**Value (Example)**</td></tr><tr><td>VPC Id</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/VPC/ID</td><td>/global/mdl/prod/VPC/ID</td><td>{{VPCID}}</td><td>vpc-abc01234</td></tr><tr><td>Private Subnet List</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/VPC/SubnetIDs/private</td><td>/global/mdl/prod/VPC/SubnetIDs/private</td><td>{{PrivateSubnetsList}}</td><td>subnet-abc01234,subnet-abc56789</td></tr><tr><td>Public Subnet List</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/VPC/SubnetIDs/public</td><td>/global/mdl/prod/VPC/SubnetIDs/public</td><td>{{PublicSubnetsList}}</td><td>subnet-abc01234,subnet-abc56789</td></tr></table> |

**CreateKeypair** 

|   |   |
| ----- | ----- |
| **Name** | CreateKeypair |
| **Description** | Specifies whether to create new KeyPair or to use existing KeyPair. User needs to fill the SSM parameters as per below information in case of using existing KeyPair. Note that the private key will be uploaded to parameter store in case MDL creates the keys. |
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |
| **Input SSM Parameters** (required if false) | <table><tr><td>**Description**</td><td>**Name (Format)**</td><td>**Name (Example)**</td><td>**Value (Format)**</td><td>**Value (Example)**</td></tr><tr><td>KeyPair Name</td><td>/app/MDL/{{MDLInstanceName}}/{{Environment}}/KEYS/KeypairName</td><td>/app/MDL/mdl/prod/KEYS/KeypairName</td><td>{{KeypairName}}</td><td>app_mdl_prod</td></tr></table> |
| **Output SSM Parameters** (in case of true) | <table><tr><td>**Description**</td><td>**Name (Format)**</td><td>**Name (Example)**</td><td>**Value (Format)**</td><td>**Value (Example)**</td></tr><tr><td>Private Key Created</td><td>/{{KeypairName}}</td><td>/app_mdl_prod</td><td>{{PrivateKey}} - **This is Secure String**</td><td>-----BEGIN RSA PRIVATE KEY----- MIIEowIBAAKCAQEAy6fMbEN605odHWNJTEItk7VmOIsff3+YLzrAHGGCv7cvwEZapaoqzZGqiydI 0fSCpHASdo5bLv1tjr0Z+mNIf6OEj8M07FudDJ2Zfkw5hx6rRjxGT0Qf0UXNRElQbH/mlxCsMcRX empCdBA+yqH0VPqekhB1IwrMhxfpUVLvJk4LRa5JU+tVzIGUgSBK+PLlaCVfEmcC5evaQ1PJjSmn hErEZ5qwo3pSrIi5tOay+uuNgwjygQarRhq19HKoRNS2G0+uP3uae0bO3magXfSVS7KEw+8ttGLU 1nLHn5FQqeTPssweot1mOqyK0m4tLTBDimo11bcBrqKYgCy4M+fPeQIDAQABAoIBAH7CuuLIPbNn waeBHSZyKpw91JptPfXGHZuIHfuMVi2uf/JV2CY1fN7nRBfJI/JLFuXzPAq/INJmu8KUwY6wLXgE <span>2pd2A2sq0p2QkNqwUZG1APHgJmECgYEA5Mc25rWSiEi8xxN3k6aeWqZ7ZCF0HVL2WBrXjQnyw8Mg</span> 4tPTozdbUDnwwidFizNcrBorUZ0TM9s4aBnrUicKkXgG7ueUZLE2u48ejye0BxBoU+voIqz3BWpo L2b/skU+PpyIIwhKvgdxcaLWj25UsAg9q7gRRHwbfBsFZtc7yt1guQ/hwWYXEh1yvSq7W9i5bhd4 2pd2A2sq0p2QkNqwUZG1APHgJmECgYEA5Mc25rWSiEi8xxN3k6aeWqZ7ZCF0HVL2WBrXjQnyw8Mg ixl1iPMX8KxQTAc2dnR5Ct69HiOBTYZZdtWM2JvtiPi3BawQOxsH89KgCOW9zyHlaD7ayynWrabN LrFvVrrzFVmMssJOnpKLwx6VJoCDBWMaFsQYjEN/tdyeqTnNnbsCgYEA4+NS6PgqzHRoE1KAU6kO TE9rM7WiV0aQ8VkVBQtaKFnR1NtPlwGl0S3chkC3UwkfZ5JjQ61Nx8DBeTEHpxc4K0OI4xbOnYzn J/sSU9s9K/9TwymlKsiV4T3u24y3LiYEZzq+Bjne6uAsyWdkCIe4q/JOen3K83cT8oqcjSbHWlsC gYAC0S0s5Bl80iB49xVm3QtgJGKqlfrfDZF4/kOfOfsiS/nPnK2k1RF7ZjPK69/Qz5hZ+OotP9Ss xrW9T93fIPRo1l8yk67Te366kuJjmaifr1Qq13NMQySgmMg4BflQARdTMPoZjWj4bOeJrIu7oKN8 Yn8Evr9qor4k2CWgAdU1VwKBgC4i06I+u8twtbFTvo8xZqekXHu3hgpNSwLRmiPPTI1mwchqLg7Q UpWqxz6W04aIDkeVp2sIJvsN1x2GA5qcZM69eXUgJOxYnmPFvMwlUpkZtAeK4tlqio1zUGw6bMt2 /uU3S0lPZgX/<span>UpWqxz6W04aIDkeVp2sIJvsN1x2GA5qcZM69eXUgJOxYnmPFvMwlUpkZtAeK4tlqio1zUGw6bMt2</span> Mb2/EXlHHhsl7uZKdXQIdRPeQIxT/8XAAjDszCpy2UbG7t8CYfck0rH3r6VeefLocP2jF7550aB7 1UJX47Wud7wKNi0lVwDSahkA2/Va7aaaIH72ZaWszGwFuk1GiDX01UiNh/VjtVKhiTPU</p><p>-----END RSA PRIVATE KEY-----</td></tr></table> |
  

**CreateCloudFrontDistribution** 

|   |   |
| ----- | ----- |
| **Name** | CreateCloudFrontDistribution |
| **Description** | Specifies whether to create new Cloudfront Distribution or to use existing Cloudfront Distribution. User needs to fill the SSM parameters as per below information in case of using existing Cloudfront Distribution. Here is the [template](images/CloudFrontDistribution.json) to create one. |
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |

**CreateDemoObjects** 

|   |   |
| ----- | ----- |
| **Name** | CreateDemoObjects |
| **Description** | Specifies whether to create demo data in the data lake. |
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |

**EnableSSLAndAuth** 

|   |   |
| ----- | ----- |
| **Name** | EnableSSLAndAuth |
| **Description** | Specifies whether to enable Authentication for Herd/BDSQL/Shepherd. If Authentication is enabled, MDL uses OpenLDAP to perform authentication/authorization |
| **Required** | Yes |
| **Default Value** | false |
| **Allowed Values** | true, false |

**RefreshDatabase** 

|   |   |
| ----- | ----- |
| **Name** | RefreshDatabase |
| **Description** | Specifies whether to refresh RDS for both Herd and Metastor. This is disabled during stack updates. |
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |
  
### RDS Parameters

These parameters are related to RDS

**HerdDBClass** 

|   |   |
| ----- | ----- |
| **Name** | HerdDBClass |
| **Description** | Specifies the required Database Class for Herd RDS |
| **Required** | Only If (CreateRDSInstances == true) |
| **Default Value** | db.m4.large |
| **Allowed Values** | Refer to [AWS RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Concepts.DBInstanceClass.html) documentation for valid values |
  
**HerdDBSize**

|   |   |
| ----- | ----- |
| **Name** | HerdDBSize |
| **Description** | Specifies the required Database Size for Herd RDS (in GB) |
| **Required** | Only If (CreateRDSInstances == true) |
| **Default Value** | 10 |
| **Allowed Values** | Refer [AWS RDS](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-rds-database-instance.html#cfn-rds-dbinstance-allocatedstorage) documentation for more details |

**MetastorDBClass** 

|   |   |
| ----- | ----- |
| **Name** | MetastorDBClass |
| **Description** | Specifies the required Database Class for Metastor RDS |
| **Required** | Only If (CreateRDSInstances == true) |
| **Default Value** | db.m4.large |
| **Allowed Values** | Refer to [AWS RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Concepts.DBInstanceClass.html) documentation for valid values |

**MetastorDBSize** 

|   |   |
| ----- | ----- |
| **Name** | MetastorDBSize |
| **Description** | Specifies the required Database Size for Metastor RDS (in GB) |
| **Required** | Only If (CreateRDSInstances == true) |
| **Default Value** | 10 |
| **Allowed Values** | Refer [AWS RDS](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-rds-database-instance.html#cfn-rds-dbinstance-allocatedstorage) documentation for more details |

### Web Domain and Certificate Parameters

These parameters are related to Certificates and Domains. These are required only if EnableSSLAndAuth = true

**CertificateArn** 

|   |   |
| ----- | ----- |
| **Name** | CertificateArn |
| **Description** | Specifies the Arn information from ACM for the Certificate to be used in MDL. Refer [AWS documentation](https://docs.aws.amazon.com/acm/latest/userguide/acm-overview.html) to create Certificates in ACM. |
| **Required** | Only If (EnableSSLAndAuth == true) |
| **Default Value** |  |
| **Allowed Values** | Refer [AWS documentation](https://docs.aws.amazon.com/acm/latest/userguide/acm-overview.html) for getting the ARN of the Certificate. Note that the certificate is used for three end points: Herd, Shepherd, and Bdsql. So, Certificate should have [Wildcard Domain Name](https://aws.amazon.com/certificate-manager/faqs/#acm-certificates). The Certificate should match any first level subdomain. Format - ***.domainName** (Example: ***.example.com**). MD prefixes corresponding first level subdomain (Example: **mdlHerd.example.com**, **mdlShepherd.example.com**, and **mdlBdsql.example.com**). |

**DomainNameSuffix** 

|   |   |
| ----- | ----- |
| **Name** | DomainNameSuffix |
| **Description** | Specifies the Domain Name Suffix as per the Certificate specified in "CertificateArn" |
| **Required** | Only If (EnableSSLAndAuth == true) |
| **Default Value** |  |
| **Allowed Values** | Refer [AWS documentation](https://docs.aws.amazon.com/acm/latest/userguide/setup-domain.html) for setting up a new Domain. When "EnableSSLAndAuth" option is enabled, MDL uses this DomainNameSuffix for the Route53 configurations. So, user needs to own this specified domain. And, this Domain name must match the certificate specified in "CertificateArn" parameter. |

**HostedZoneName** 

|   |   |
| ----- | ----- |
| **Name** | HostedZoneName |
| **Description** | Specifies the HostedZoneName for Route53 configuration |
| **Required** | Only If (EnableSSLAndAuth == true) |
| **Default Value** |  |
| **Allowed Values** | Refer [AWS Documentation](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/CreatingHostedZone.html) for more details about creating Hosted Zone. When "EnableSSLAndAuth" option is enabled, MDL uses this HostedZoneName for the Route53 configurations. So, user needs to own this specified domain related to the HostedZone. And, this Domain name must match the certificate specified in "CertificateArn" parameter. |  

**CertificateInfo** 

|   |   |
| ----- | ----- |
| **Name** | CertificateInfo |
| **Description** | Specifies the Certificate Information for creating self-signed certificates |
| **Required** | Only If (EnableSSLAndAuth == true) |
| **Default Value** |  |
| **Allowed Values** | Format of: CN=<>,OU=<>,O=<>,L=<>,ST=<>,C=<> |  

**LdapDN** 

|   |   |
| ----- | ----- |
| **Name** | LdapDN |
| **Description** | Specifies the LDAP Domain name used in OpenLDAP configuration |
| **Required** | Only If (EnableSSLAndAuth == true) |
| **Default Value** |  |
| **Allowed Values** | ^(dc=\[^=\]+,)*(dc=\[^=\]+)$ |  

### EC2 Instance Parameters

These parameters describe the instance types for various EC2 that are use to run components of the Herd-MDL product

**EsInstanceType** 

|   |   |
| ----- | ----- |
| **Name** | EsInstanceType |
| **Description** | Specifies the instance type for Elastic Search EC2 |
| **Required** | Yes |
| **Default Value** | t2.medium |
| **Allowed Values** | Refer [AWS Documentation](https://aws.amazon.com/ec2/instance-types/) for more details |  

**HerdInstanceType** 

|   |   |
| ----- | ----- |
| **Name** | HerdInstanceType |
| **Description** | Specifies the instance type for Herd EC2 |
| **Required** | Yes |
| **Default Value** | m4.2xlarge |
| **Allowed Values** | Refer [AWS Documentation](https://aws.amazon.com/ec2/instance-types/) for more details |  
  
**LdapInstanceType** 

|   |   |
| ----- | ----- |
| **Name** | LdapInstanceType |
| **Description** | Specifies the instance type for OpenLDAP EC2 |
| **Required** | Yes |
| **Default Value** | t2.small |
| **Allowed Values** | Refer [AWS Documentation](https://aws.amazon.com/ec2/instance-types/) for more details |  

**MetastorInstanceType** 

|   |   |
| ----- | ----- |
| **Name** | MetastorInstanceType |
| **Description** | Specifies the instance type for Metastor EC2 |
| **Required** | Yes |
| **Default Value** | m4.2xlarge |
| **Allowed Values** | Refer [AWS Documentation](https://aws.amazon.com/ec2/instance-types/) for more details |  

**BdsqlMasterInstanceType** 

|   |   |
| ----- | ----- |
| **Name** | BdsqlMasterInstanceType |
| **Description** | Specifies the instance type for BDSQL Presto EMR Cluster Master Instance |
| **Required** | Yes |
| **Default Value** | m4.4xlarge |
| **Allowed Values** | Refer [AWS Documentation](https://aws.amazon.com/ec2/instance-types/) for more details |  

**BdsqlCoreInstanceType** 

|   |   |
| ----- | ----- |
| **Name** | BdsqlMasterInstanceType |
| **Description** | Specifies the instance type for BDSQL Presto EMR Cluster Core Instance |
| **Required** | Yes |
| **Default Value** | m4.4xlarge |
| **Allowed Values** | Refer [AWS Documentation](https://aws.amazon.com/ec2/instance-types/) for more details |  

**NumberOfBdsqlCoreInstances** 

|   |   |
| ----- | ----- |
| **Name** | NumberOfBdsqlCoreInstances |
| **Description** | Specifies the number of Core Instances for BDSQL Presto EMR Cluster |
| **Required** | Yes |
| **Default Value** | 1 |
| **Allowed Values** | Integer |
  
### Tag Parameters

These parameters describe the tag information for the AWS resources created by MDL.

**CustomTagName** 

|   |   |
| ----- | ----- |
| **Name** | CustomTagName |
| **Description** | Specifies the Tag Name to be applied to all the AWS resources created by MDL |
| **Required** | No |
| **Default Value** |  |
| **Allowed Values** | Refer [AWS Documentation](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Using_Tags.html#adding-or-deleting-tags) |
  
**CustomTagValue** 

|   |   |
| ----- | ----- |
| **Name** | CustomTagValue |
| **Description** | Specifies the Tag Value to be applied to all the AWS resources created by MDL |
| **Required** | No |
| **Default Value** |  |
| **Allowed Values** | Refer [AWS Documentation](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Using_Tags.html#adding-or-deleting-tags) |