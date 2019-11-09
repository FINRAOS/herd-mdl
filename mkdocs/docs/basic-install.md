
Herd-MDL Basic Install
======================

The Basic Install is an easy, turnkey installation of Herd-MDL. This install is fully automated. It uses CloudFormation templates to create AWS resources then deploys Herd-MDL product, 
integrates the components, and creates sample data.

See [Advanced Install](advanced-install.md) for other options. For example if your organization requires that certain AWS resources such as IAM Roles, 
Security Groups, etc. are created outside Herd-MDL automated install. The Advanced Install allows for optional creation of AWS resources through other 
mechanisms and provides detailed specifications on what to create and how to provide references to Herd-MDL automated install.

## Prerequisites

These are prerequisites that are necessary for installing MDL components for Basic Installation Type

*   An [AWS](https://aws.amazon.com) account 
*   User who has power user access as per this policy - [arn:aws:iam::aws:policy/PowerUserAccess](https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies_job-functions.html#jf_developer-power-user)
    *   MDL deployment creates various AWS resources like Cloudformation, EC2, IAM, Security Groups, S3 etc, and power user access is needed for creating these resources
    *   Sample IAM policy for [PowerUserAccess](attachments/1070290969/1070291504.txt)

## Steps

Installation is automated through CloudFormation templates in AWS. The stack creates all the resources required by MDL application. This takes a couple of hours to create all the resources needed for MDL. A stack can be created using AWS console, or AWS CLI, or AWS SDK. Refer [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/stacks.html) for creating stacks using Cloudformation templates. In this section, steps are described for creating the stack using AWS console.

1.  Download release artefact: [installMDL.yml](https://github.com/FINRAOS/herd-mdl/releases/download/mdl-v1.5.0/installMDL.yml) to your local file system, this will install version `1.5.0`.
2.  Login to AWS console and navigate to the [Cloudformation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-console-login.html) service.
3.  Create a new stack using the option: "Upload a template to Amazon S3" - Refer to [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-template.html) on how to select a local template.
4.  Select the same `installMDL.yml` file from your local file system (which was downloaded in step 1).
5.  On the next page
    *   Enter a unique value for the 'stack name' parameter. 
    
        > Note: A stack name can contain only alphanumeric characters (case-sensitive) and hyphens. It must start with an alphabetic character and can't be longer than 128 characters. Further reading: [Specifying Stack Name and Parameters](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-parameters.html)
    
    *   Leave all other parameters to their default values and click 'Next'.
      
6.  On the next page, specify any additional stack options, viz., tags, termination protection etc. for your stack and click 'Next'. Further reading on tags: [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-console-add-tags.html)
7.  Review the information on the next page and click on the 'Create' button, this will initiate stack creation.
8.  Wait for 'CREATE_COMPLETE' on the stack and all its nested stacks.
9.  Proceed to the [User Guide](user-guide.md) to explore Herd-MDL.

