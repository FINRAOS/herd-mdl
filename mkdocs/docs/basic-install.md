
Herd-MDL Basic Install
======================

The Basic Install is an easy, turnkey installation of Herd-MDL. This install is fully automated. It uses CloudFormation templates to create AWS resources then deploys Herd-MDL product, integrates the components, and creates sample data

See [Advanced Install](advanced-install.md) for other options. For example if your organization requires that certain AWS resources such as IAM Roles, Security Groups, etc. are created outside Herd-MDL automated install. The Advanced Install allows for optional creation of AWS resources through other mechanisms and provides detailed specifications on what to create and how to provide references to Herd-MDL automated install

## Prerequisites

These are prerequisites that are necessary for installing MDL components for Basic Installation Type

*   An [AWS](https://aws.amazon.com) account 
*   User who has power user access as per this policy - [arn:aws:iam::aws:policy/PowerUserAccess](https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies_job-functions.html#jf_developer-power-user)
    *   MDL deployment creates various AWS resources like Cloudformation, EC2, IAM, Security Groups, S3 etc, and power user access is needed for creating these resources
    *   Sample IAM policy for [PowerUserAccess](attachments/1070290969/1070291504.txt)

## Steps

Installation is automated through Cloudformation templates in AWS. The stack creates all the resources required by MDL application. This takes a couple of hours to create all the resources needed for MDL. A stack can be created using AWS console, or AWS CLI, or AWS SDK. Refer [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/stacks.html) for creating stacks using Cloudformation templates. In this section, steps are described for creating the stack using AWS console.

*   Download the attached [[installMDL.yml](https://github.com/FINRAOS/herd-mdl/releases/download/mdl-v1.1.0/installMDL.yml) file to local file system
*   Login to AWS console and navigate to [Cloudformation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-console-login.html)
*   Create the stack using option "Upload a template to Amazon S3" - Refer [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-template.html) for selecting a local template
*   Choose the installMDL.yml file from local file system
*   In the next page, 
    *   Enter the values for [Stack Name](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-parameters.html)  
        *   A stack name can contain only alphanumeric characters (case-sensitive) and hyphens. It must start with an alphabetic character and can't be longer than 128 characters.
    *   Enter the following parameter  
        *   ReleaseVersion
            *   1.2.0
    *   Leave all other parameters to their default values          
*   In the next page, specify the stack options as per [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-console-add-tags.html)
*   Review the parameters, and create the stack as per [AWS documentation](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-using-console-create-stack-review.html)
*   Wait for "CREATE_COMPLETE" on the stack and all nested stacks.
*   Proceed to the [User Guide](user-guide.md) to explore Herd-MDL

