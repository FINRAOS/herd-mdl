
Herd-MDL Functionality Test : MDLT
==============================

## Overview
MDLT refers to an automated test-framework for Herd-MDL, it includes bringing up herd-mdl stack, running tests against herd-mdl stack, shutting down herd-mdl stack. 

## Prerequisites

These are prerequisites that are necessary for installing MDL components for Basic Installation Type

*   See [Herd-MDL Prerequisites](basic-install.md) for Herd-MDL prerequisites. 
*   Existing EC2 Key Value Pair


## Simple MDLT Execution(noAuth)

*   Download the attached [mdlt.yml](https://github.com/FINRAOS/herd-mdl/releases/download/mdl-v1.2.0/mdlt.yml) file to local file system
*   Login to AWS console and navigate to [Cloudformation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-console-login.html)
*   Create the stack using option "Upload a template to Amazon S3" - Refer [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-template.html) for selecting a local template
*   Choose the mdlt.yml file from local file system
*   In the next Parameters Edit page, 
    *   Enter the values for [Stack Name](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-parameters.html)  
        *   A stack name can contain only alphanumeric characters (case-sensitive) and hyphens. It must start with an alphabetic character and can't be longer than 128 characters.
    *   Select any existing ec2 key pair from dropdown for parameter: [KeyPairName](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html)
    *   Leave all other parameters to their default values, only Herd-MDL stack without auth will be created by default values. 
    * Please refer to MDLT Stack Parameters Specifications for creating Herd-MDL stack with authentication/authorization.
*   In the next page, specify the stack options as per [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-console-add-tags.html)
*   Review the parameters, and create the stack as per [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-review.html)
*   Wait for "CREATE_COMPLETE" on the stack and all nested stacks.(approximate wait time here is 2 hours)
*   Checkout below cloudwatch documentation to find test execution results
*   Wait for the mdlt DeployHost stack to be deleted.(approximate wait time here is 1.5 hour)
    *  **mdlt DeployHost stack name format**: ${MDLInstanceName}-DeployHostHttpWithoutAuth-${awsUniqueNumber}(eg. mdlt-DeployHostHttpWithoutAuth-A9915FUEU3JI)
*   if CreateVPC==true while creating mdlt, you need to delete vpc manually, steps see [MDLT Known Issues](#mdlt-known-issues) 
*   Delete mdlt wrapper stack from aws CloudFormation console, step below [MDLT Known Issues](#mdlt-known-issues).(All nested stacks are deleted automatically by mdlt)

## MDLT with Auth stack
*   Same steps as above Simple Installation, but in stack parameters edit page, filling valid values for following parameters.
    *   [MdlAuthStackName]((https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-parameters.html)  ):  empty parameter value for MdlNoAuthStackName, and enter auth stack name in parameter MdlAuthStackName
    *   [CertificateArn](https://docs.aws.amazon.com/acm/latest/userguide/acm-overview.html):  valid aws certificate ARN, refer to Herd-MDL Advanced install[advanced-install.md] for more information
    *   [DomainNameSuffix](https://docs.aws.amazon.com/acm/latest/userguide/setup-domain.html):  domain name suffix, refer to Herd-MDL Advanced install[advanced-install.md] for more information
    *   [HostedZoneName](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/CreatingHostedZone.html):  hosted zone name, refer to Herd-MDL Advanced install[advanced-install.md] for more information


## MDLT against Existing stack
*   Same steps as above Simple Installation, but in stack parameters edit page, filling different values for following parameters
    *   If existing stack is noAuth stack, enter existing stack name in parameter [MdlNoAuthStackName](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-parameters.html) ; if existing stack is auth stack, empty parameter value for MdlNoAuthStackName, and enter existing stack name in parameter [MdlAuthStackName]((https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-parameters.html)  )
    *   set parameter CreateVPC value to false
    *   Enter correct value for [MdlPrivateSubnets](https://docs.aws.amazon.com/AmazonVPC/latest/UserGuide/VPC_Subnets.html), which is the existing stack PrivateSubnets value(can be found in existing stack VPC SSM parameter, please refer to Herd-MDL docs for more info)
    *   Enter correct value for [MdlPublicSubnets](https://docs.aws.amazon.com/AmazonVPC/latest/UserGuide/VPC_Subnets.html), which is the existing stack PublicSubnets value (can be found in existing stack VPC SSM parameter, please refer to Herd-MDL docs for more info)
    *   Enter correct value for [MdlVpcId](https://docs.aws.amazon.com/AmazonVPC/latest/UserGuide/VPC_Subnets.html), which is the existing stack vpc id value (can be found in existing stack VPC SSM parameter, please refer to Herd-MDL docs for more info)


## MDLT CFT Specifications


### Deployment Parameters

These parameters are related to which version and components to deploy.

**ReleaseVersion**

|   |   |
| ----- | ----- |
| **Name** | ReleaseVersion |
| **Description** | Release version of MDL application to install |
| **Required** | Yes |
| **Default Value** | 1.2.0 |
| **Allowed Value** | 1.2.0 |

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

**MDLInstanceName**

|   |   |
| ----- | ----- |
| **Name** | MDLInstanceName |
| **Description** | Name of the Application being installed |
| **Required** | Yes |
| **Default Value** | mdlt |
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

**MdltResultS3BucketName**

|   |   |
| ----- | ----- |
| **Name** | MdltResultS3BucketName |
| **Description** | existing s3 bucket name to save mdlt test execution results, if this parameter leaves empty, test execution results will be saved to mdlt staging bucket: ${AWS::AccountId}-${MDLInstanceName}-mdlt-${Environment} |
| **Required** | NO |

### MDL Stack Name Parameters

These parameters define the herd-mdl stack names, either existing stack or stack to be created

**MdlNoAuthStackName**

|   |   |
| ----- | ----- |
| **Name** | MdlNoAuthStackName |
| **Description** | stack name of existing noAuth herd-mdl stack, or stack name to be used for new herd-mdl noAuth stack creation; when this value leaves empty, mdlt will not create MDL noAuth stack|
| **Required** | NO |
| **Default Value** | mdltNoAuth |
| **Constraint** | must be a non-existing stack name if wants mdlt to create new herd-mdl stack |


**MdlAuthStackName**

|   |   |
| ----- | ----- |
| **Name** | MdlAuthStackName |
| **Description** | stack name of existing auth herd-mdl stack, or stack name to be used for new herd-mdl auth stack creation; when this value leaves empty, mdlt will not create MDL auth stack|
| **Required** | NO |
| **Constraint** | must be a non-existing stack name if wants mdlt to create new herd-mdl stack |

### Conditional Parameters

These are conditional parameters to decide whether MDL creates certain resources or MDL uses existing resources. In each case where a parameter is false, SSM parameters must be present that allow MDL to reference the resources that have been created prior to running the Herd-MDL automated install. 

**CreateVPC** 

|   |   |
| ----- | ----- |
| **Name** | CreateVPC |
| **Description** | Specifies whether to create new VPC/Subnets or to use existing VPC/Subnets. User needs to fill the SSM parameters as per below information in case of using existing VPC/Subnets. |
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |
| **VPC Parameters** (required if false) | Enter all bellow VPC Parameters with correct value while creating the mdlt stack|


**RollbackOnFailure** 

|   |   |
| ----- | ----- |
| **Name** | CreateVPC |
| **Description** | Specifies whether to shutdown herd-mdl stack after mdlt execution, this only has effects on herd-mdl stack created by mdlt; when RollBackOnFailure==true, herd-mdl stack will be deleted automatically after test execution; when set to false, herd-mdl stack created by mdlt will not be deleted; mdlt execution will not delete any existing herd-mdl stack whatever the RollBackOnFailure value is|
| **Required** | Yes |
| **Default Value** | true |
| **Allowed Values** | true, false |


### EC2 Instance Parameters

These parameters describe the EC2 instance related parameters

**ImageId** 

|   |   |
| ----- | ----- |
| **Name** | ImageId |
| **Description** | AMI id for the EC2 instances. Note that OSS user may use any other AMI which is similar to amzn-ami-hvm-2017.09.1.20180307-x86_64-gp2. However, there could be some issues in terms of package installation/availability, while using a different AMI. So, it is user's responsibility to make sure provided AMI has all the packages like amzn-ami-hvm-2017.09.1.20180307-x86_64-gp2 |
| **Required** | Yes |
| **Default Value** | ami-1853ac65 |

**InstanceType** 

|   |   |
| ----- | ----- |
| **Name** | InstanceType |
| **Description** | Specifies the instance type for Mdlt Deploy Host EC2 |
| **Required** | Yes |
| **Default Value** | t2.medium |
| **Allowed Values** | Refer [AWS Documentation](https://aws.amazon.com/ec2/instance-types/) for more details |  

**KeyPairName** 

|   |   |
| ----- | ----- |
| **Name** | KeyPairName |
| **Description** | Specifies the existing key pair name to be used for Mdlt Deploy Host EC2 |
| **Required** | Yes |
| **Allowed Values** | Refer [AWS Documentation](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html) for more details |  

### VPC Parameters

These parameters are related to AWS VPC id, private subnets and public subnet. These are required only if CreateVPC = false

**MdlVpcId** 

|   |   |
| ----- | ----- |
| **Name** | MdlVpcId |
| **Description** | existing vpc id to be used for herd-mdl stack creation |
| **Required** | Only If (CreateVPC==false) |
| **Allowed Values** | Refer [AWS Documentation](https://docs.aws.amazon.com/AmazonVPC/latest/UserGuide/VPC_Subnets.html) for more details |  

**MdlPrivateSubnets** 

|   |   |
| ----- | ----- |
| **Name** | MdlPrivateSubnets |
| **Description** | existing private subnets list to be used for herd-mdl stack creation |
| **Required** | Only If (CreateVPC==false) |
| **Allowed Values** | Refer [AWS Documentation](https://docs.aws.amazon.com/AmazonVPC/latest/UserGuide/VPC_Subnets.html) for more details |  

**MdlPublicSubnets** 

|   |   |
| ----- | ----- |
| **Name** | MdlPublicSubnets |
| **Description** | existing public subnets to be used for herd-mdl stack creation |
| **Required** | Only If (CreateVPC==false) |
| **Allowed Values** | Refer [AWS Documentation](https://docs.aws.amazon.com/AmazonVPC/latest/UserGuide/VPC_Subnets.html) for more details |  

### Web Domain and Certificate Parameters

These parameters are related to Certificates and Domains. These are required only if MdlAuthStackName has value

**CertificateArn** 

|   |   |
| ----- | ----- |
| **Name** | CertificateArn |
| **Description** | Specifies the Arn information from ACM for the Certificate to be used in MDL. Refer [AWS documentation](https://docs.aws.amazon.com/acm/latest/userguide/acm-overview.html) to create Certificates in ACM. |
| **Required** | Only If (MdltAuthStack is not empty) |
| **Default Value** |  |
| **Allowed Values** | Refer [AWS documentation](https://docs.aws.amazon.com/acm/latest/userguide/acm-overview.html) for getting the ARN of the Certificate. Note that the certificate is used for three end points: Herd, Shepherd, and Bdsql. So, Certificate should have [Wildcard Domain Name](https://aws.amazon.com/certificate-manager/faqs/#acm-certificates). The Certificate should match any first level subdomain. Format - ***.domainName** (Example: ***.example.com**). MD prefixes corresponding first level subdomain (Example: **mdlHerd.example.com**, **mdlShepherd.example.com**, and **mdlBdsql.example.com**). |

**DomainNameSuffix** 

|   |   |
| ----- | ----- |
| **Name** | DomainNameSuffix |
| **Description** | Specifies the Domain Name Suffix as per the Certificate specified in "CertificateArn" |
| **Required** | Only If (MdltAuthStack is not empty) |
| **Default Value** |  |
| **Allowed Values** | Refer [AWS documentation](https://docs.aws.amazon.com/acm/latest/userguide/setup-domain.html) for setting up a new Domain. When "EnableSSLAndAuth" option is enabled, MDL uses this DomainNameSuffix for the Route53 configurations. So, user needs to own this specified domain. And, this Domain name must match the certificate specified in "CertificateArn" parameter. |

**HostedZoneName** 

|   |   |
| ----- | ----- |
| **Name** | HostedZoneName |
| **Description** | Specifies the HostedZoneName for Route53 configuration |
| **Required** | Only If (MdltAuthStack is not empty) |
| **Default Value** |  |
| **Allowed Values** | Refer [AWS Documentation](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/CreatingHostedZone.html) for more details about creating Hosted Zone. When "EnableSSLAndAuth" option is enabled, MDL uses this HostedZoneName for the Route53 configurations. So, user needs to own this specified domain related to the HostedZone. And, this Domain name must match the certificate specified in "CertificateArn" parameter. |  
    
## MDLT Execution CloudWatch logs
**Steps:**

*   Login to AWS Console and navigate to CloudWatch
*   Click on 'Logs' in the left panel
*   Filter Log Groups with stack cloudwatch log group name
    *   **where to find mdlt log group name?**: find the stack with Description 'MDL - Functional Test Deploy Host' from AWS CloudFormation console, the stack name is the mdlt log group name.(Example: mdlt-DeployHostHttpWithoutAuth-FUPHGEXHSQLX)
*   Click on above filtered stack log group to open it, inside this stack log group folder, you can find following mdlt logs:
      *   mdlt cfn-init log,
      *   mdlt setup log
      *   mdlt functionality test execution log
      *   mdlt junit jupiter log
      *   mdlt shutdown log
      
## MDLT Known Issues

### The vpc 'vpc-xxxxxx' has dependencies and cannot be deleted
*   Steps to delete the vpc manually:
    *   find vpc id from createVpc stack output parameter VPC. 
    *   go to aws vpc console and click Your VPCs 
    *   enter the vpc id found in step one. 
    *   select your vpc, click button Actions, Delete Vpc, select the checkbox to delete connect, confirm vpc deletion by clicking the button 'Yes, Delete'
    
### After MDLT execution, mdlt wrapper stack is not deleted automatically.
*   Solution steps to delete mdlt wrapper stack maunally:
    *   login to aws cloudformation console
    *   search by mdlt wrapper stack name, choose the mdlt wrapper stack, click Actions, select 'Delete Stack'.( mdlt wrapper stack name is the stackName you used when creating mdlt stack)
