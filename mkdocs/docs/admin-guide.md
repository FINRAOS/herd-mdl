Herd-MDL Administration Guide
========================

## How to SSH to an EC2 Instance in MDL

**Prerequisites**

*   AWS Console Access of the AWS Account, where MDL is created.
*   SSH Client (Example Putty).
*   MDL Instance Name of the MDL stack.
    *   This is the parameter to the MDL Cloudformation Stack.
*   Bastion Host in the VPC in case of connectivity issues to the private subnet.
  
**Steps**

*   Login to AWS Console and navigate to SSM Parameter section (Refer [AWS Documentation](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-console.html))
*   Search for the parameter: /app/MDL/${MDLInstanceName}/${Environment}/KEYS/KeypairName
    *   Example : _/app/MDL/mdl/dev/KEYS/KeypairName_
*   Get the Value for the above parameter. That value specifies the keypair-name which holds the pem file
    *   Example: _app_mdl_dev_
*   Get Parameter "app_mdl_dev" - Value from the previous step
    *   The value of this parameter is a SecureString which is the private-key material for the keypair created for your stack. Copy the contents into a new file: `key.pem` and make it read-only: 
>`$ chmod 400 key.pem`
*   Login to the node using SSH client with user name "ec2-user" and the PEM file from previous step: 
>`$ ssh -i /path/to/key.pem ec2-user@<ip-address-ec2>`
    *   Default AMI has ec2-user configuration

## How to find MDL User Credentials to login to Herd/Shepherd/Bdsql

This section describes how to locate credentials required for endpoints when you have installed with EnableSSLAndAuth=true.
> Note: A detailed description and a list of all default users and auth groups created for your stack can be found in the [manage OpenLdap section](#managing-openldap-users-and-groups)

**Prerequisites**

*   AWS Console Access of the AWS Account, where MDL is created
*   MDL Instance Name of the MDL stack
    *   This is the parameter to the MDL Cloudformation Stack
*   EnableSSLAndAuth must be set to true while creating the stack

**Steps**

*   Login to AWS Console and navigate to SSM Parameter section (Refer [AWS Documentation](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-console.html))
*   User Name
    *   Find the parameter: /app/MDL/${MDLInstanceName}/${Environment}/LDAP/User/HerdAdminUsername on the console
        *   Example :  `/app/MDL/mdlstack/dev/LDAP/User/HerdAdminUsername`
    *   Get the Value for the above parameter. That value specifies the user name for Herd/Bdsql/Shepherd
        *   Example: `herd_admin_user`
*   Password  
    *   Find the parameter: /app/MDL/${MDLInstanceName}/${Environment}/Password/HerdAdminPassword on the console
        *   Example :  `/app/MDL/mdlstack/dev/LDAP/Password/HerdAdminPassword`
    *   Get the Value for the above parameter, it is a 12-letter AlphaNumeric String which specifies the password for Herd/Bdsql/Shepherd, and it is a Secure String
        *   Example: `ODMyOTdmZmE5`
*   Use the above User name, and Password to login to Herd/Shepherd/Bdsql  


## How-tos for initial creation of Herd metadata 


Refer to [Herd Documentation](https://github.com/FINRAOS/herd/wiki/quick-start-to-registering-data#create-a-new-storage-instance) for details on the following:
*  Add Storage in Herd
*  Create New Namespace in Herd
*  Create New Business Object in Herd
*  Create New Format in Herd
*  Register Data in Herd

## How to propagate an 'Object' from Herd to Metastor

Once Business Object is created in Herd, there is an additional step to include the Object in Metastor so that the Object is query-able in BDSQL. This step registers a Notification for the Object such that an Activity workflow is triggered for every Data Registration to this Object. This is a one time step for the Object. Once Object is registered for the Notification, all the future partitions registered for the Object will be available in BDSQL.

Here is the XML to add the Notification in Herd

**objectNotification.xml**
```
<businessObjectDataNotificationRegistrationCreateRequest>
    <businessObjectDataNotificationRegistrationKey>
        <namespace>MDL</namespace>
        <notificationName>METASTOR_{{NAMESPACE}}_{{USAGE}}_{{OBJECT_NAME}}_{{FILE_FORMAT}}</notificationName>
    </businessObjectDataNotificationRegistrationKey>
    <businessObjectDataEventType>BUS_OBJCT_DATA_STTS_CHG</businessObjectDataEventType>
    <businessObjectDataNotificationFilter>
        <namespace>{{NAMESPACE}}</namespace>
        <businessObjectDefinitionName>{{OBJECT_NAME}}</businessObjectDefinitionName>
        <businessObjectFormatUsage>{{USAGE}}</businessObjectFormatUsage>
        <businessObjectFormatFileType>{{FILE_FORMAT}}</businessObjectFormatFileType>
        <newBusinessObjectDataStatus>VALID</newBusinessObjectDataStatus>
        <storageName>S3_MANAGED</storageName>
    </businessObjectDataNotificationFilter>
    <jobActions>
        <jobAction>
            <namespace>MDL</namespace>
            <jobName>addPartitionWorkflow</jobName>
        </jobAction>
    </jobActions>
    <notificationRegistrationStatus>ENABLED</notificationRegistrationStatus>
</businessObjectDataNotificationRegistrationCreateRequest>
```
NOTE: 
Replace {{NAMESPACE}} with the actual Namespace of the Object
Replace {{USAGE}} with the actual Usage of the Object
Replace {{OBJECT_NAME}} with the actual Object Name
Replace {{FILE_FORMAT}} with the actual File Type for the Object

  Here is the cURL syntax to add the Notification for the Object:

**cURL Command**
```
cat objectNotification.xml | curl -H "Content-Type: application/xml" -d @- -X POST http://HERD_DNS_NAME/herd-app/rest/notificationRegistrations/businessObjectDataNotificationRegistrations | xmllint --format -
```
  
## How To Query the Data in BDSQL

**Prerequisites**

*   AWS Console Access of the AWS Account, where MDL is created
*   SQL Client with Presto Driver
    *   Refer [Presto Documentation](https://prestodb.io/docs/current/installation/jdbc.html) for the JDBC Driver
*   MDL Instance Name of the MDL stack
    *   This is the parameter to the MDL Cloudformation Stack

**Steps**

*   Login to AWS Console and navigate to Clouformation 
*   Go to the Outputs Section of the MDL stack and Note down the value for the key "BdsqlURL"
    *   This is the Bdsql JDBC URL
    *   Example - jdbc:[presto://mdl.poc.aws.mdldomain.com:443/hive](presto://mdl.poc.aws.mdldomain.com:443/hive)
*   If (EnableSSLAndAuth == false)  
    *   Use the above URL with default user name "hadoop" and without password to login to BDSQL using SQL Client that supports Presto JDBC Driver
*   If (EnableSSLAndAuth == true)
    *   Navigate to SSM Parameter section (Refer [AWS Documentation](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-console.html)) for Username and Password
    *   User Name
        *   Open Parameter /app/MDL/${MDLInstanceName}/${Environment}/LDAP/User/HerdAdminUsername
        *   Example : /app/MDL/mdl/dev/LDAP/User/HerdAdminUsername
        *   Get the Value for the above parameter. That value specifies the user name for Herd/Bdsql/Shepherd
        *   Example: herd_admin
    *   Password  
        *   Open Parameter /app/MDL/${MDLInstanceName}/${Environment}/LDAP/Password/HerdAdminPassword
        *   Example : /app/MDL/mdl/dev/LDAP/Password/HerdAdminPassword
        *   Get the Value for the above parameter. That value specifies the password for Herd/Bdsql/Shepherd, and this parameter is a Secure String
        *   Example: ODMyOTdmZmE5
    *   Use the above URL, User name, and Password to login to Bdsql using the SQL Client that supports Presto JDBC Driver

## Managing OpenLDAP Users and Groups

MDL provides a helper script to manage users and groups. The script is deployed onto the OpenLDAP server in /home/mdladmin/deploy/mdl/scripts/.

**Usage and examples**

\# ./manageLdap.sh --help

Usage:
```manageLdap.sh --action [create_user|create_group|add_attribute|replace_attribute|add_user_to_group|remove_user_from_group|delete_user|delete_group|delete_object|show_directory] [--user name] [--password password] [--group name] [--dn distinguishedname]```

Examples:
```
  manageLdap.sh --action create_user --user userA --password password --email test@gmail.com --phone 1-234-567-8901
  manageLdap.sh --action add_attribute --user userA --attribute telephoneNumber --value 1-234-567-8901
  manageLdap.sh --action replace_attribute --user userA --attribute mail --value new@gmail.com
  manageLdap.sh --action create_group --user userA --group groupA
  manageLdap.sh --action add_user_to_group --user userB --group groupA
  manageLdap.sh --action remove_user_from_group --user userB --group groupA
  manageLdap.sh --action delete_object --dn "cn=userA,ou=People,ou=Groups,dc=domain,dc=com"
  manageLdap.sh --action delete_user --user userA
  manageLdap.sh --action delete_group --group groupA
  manageLdap.sh --action show_directory
  manageLdap.sh --help
```

**Create new user:**
```
\# ./manageLdap.sh --action create_user --user userA --password password
\# ./manageLdap.sh --action create_user --user userB --password password --email test@gmail.com 
\# ./manageLdap.sh --action create_user --user userC --password password --email test@gmail.com --phone 1-234-567-8901
```

adding new entry "cn=$username,ou=People,dc=finra,dc=org"

**Create new group:**

```\# ./manageLdap.sh --action create_group --user userA --group groupA```

adding new entry "cn=groupA,ou=Groups,dc=finra,dc=org"

**Add user to group:**

```\# ./manageLdap.sh --action add_user_to_group --user userA --group groupA```

add member:
        cn=userA,ou=People,dc=finra,dc=org
modifying entry "cn=groupA,ou=Groups,dc=finra,dc=org"

**Remove user from group:**

```\# ./manageLdap.sh --action remove_user_from_group --user userA --group groupA```

delete member:
        cn=userA,ou=People,dc=finra,dc=org
modifying entry "cn=groupA,ou=Groups,dc=finra,dc=org"

**Delete user:**

```\# ./manageLdap.sh --action delete_user --user userA```

deleting entry "cn=userA,ou=People,dc=finra,dc=org"

**Delete group:**

```\# ./manageLdap.sh --action delete_group --group groupA```

deleting entry "cn=groupA,ou=Groups,dc=finra,dc=org"

**Delete object:**

```\# ./manageLdap.sh --action delete_object --dn "uid=userA,ou=People,dc=finra,dc=org"```

**Add new user attribute:**

```\# ./manageLdap.sh --action add_attribute --user userA --attribute telephoneNumber --value 1-234-567-8901```

**Modify existing user attribute value:**

```\# ./manageLdap.sh --action replace_attribute --user userA --attribute mail --value new@gmail.com``


**Show directory information:**

```
\# ./manageLdap.sh --action show_directory
# extended LDIF
#
# LDAPv3
# base <dc=mdl,dc=org> with scope subtree
# filter: (objectclass=*)
# requesting: ALL
#

# mdl.org
dn: dc=mdl,dc=org
objectClass: dcObject
objectClass: organization
dc: mdl.org
o: mdl

# People, mdl.org
dn: ou=People,dc=mdl,dc=org
objectClass: organizationalUnit
ou: People

# Groups, mdl.org
dn: ou=Groups,dc=mdl,dc=org
objectClass: organizationalUnit
ou: Groups

# mdl_user, People, mdl.org
dn: cn=mdl_user,ou=People,dc=mdl,dc=org
objectClass: inetOrgPerson
objectClass: posixAccount
uid: mdl_user
cn: mdl_user
sn: null
userPassword:: e1NTSEF9SVhKL2NJb3Fmb2VkaVd5Tkp0U1BGN3EzOVpsdGk5RWE=
uidNumber: 10002
gidNumber: 1001
homeDirectory: /home/mdl_user
mail: mdl_user@mdl.org
loginShell: /bin/bash

# sec_user, People, mdl.org
dn: cn=sec_user,ou=People,dc=mdl,dc=org
objectClass: inetOrgPerson
objectClass: posixAccount
uid: sec_user
cn: sec_user
sn: null
userPassword:: e1NTSEF9WVlaaGJxYmhGbTRwQ1JWaERRdUdVV3gyc3l4OHlPWUU=
uidNumber: 10003
gidNumber: 1001
homeDirectory: /home/sec_user
mail: sec_user@mdl.org
loginShell: /bin/bash

# admin_user, People, mdl.org
dn: cn=admin_user,ou=People,dc=mdl,dc=org
objectClass: inetOrgPerson
objectClass: posixAccount
uid: admin_user
cn: admin_user
sn: null
userPassword:: e1NTSEF9VktJdkR2R1RLRENIOTRFK05FNXEwRUVEYWExdU5lQ0Q=
uidNumber: 10004
gidNumber: 1001
homeDirectory: /home/admin_user
mail: admin_user@mdl.org
loginShell: /bin/bash

# ro_user, People, mdl.org
dn: cn=ro_user,ou=People,dc=mdl,dc=org
objectClass: inetOrgPerson
objectClass: posixAccount
uid: ro_user
cn: ro_user
sn: null
userPassword:: e1NTSEF9L2ZodFFBSUwwU1hFUUIwc2dzSVJiSFY3VW0wUlkySWg=
uidNumber: 10005
gidNumber: 1001
homeDirectory: /home/ro_user
mail: ro_user@mdl.org
loginShell: /bin/bash

# basic_user, People, mdl.org
dn: cn=basic_user,ou=People,dc=mdl,dc=org
objectClass: inetOrgPerson
objectClass: posixAccount
uid: basic_user
cn: basic_user
sn: null
userPassword:
uidNumber: 10006
gidNumber: 1001
homeDirectory: /home/basic_user
mail: basic_user@mdl.org
loginShell: /bin/bash

# APP_MDL_ACL_RO_herd_admin, Groups, mdl.org
dn: cn=APP_MDL_ACL_RO_herd_admin,ou=Groups,dc=mdl,dc=org
cn: APP_MDL_ACL_RO_herd_admin
objectClass: top
objectClass: groupOfNames
member: cn=admin_user,ou=People,dc=mdl,dc=org

# APP_MDL_ACL_RO_herd_ro, Groups, mdl.org
dn: cn=APP_MDL_ACL_RO_herd_ro,ou=Groups,dc=mdl,dc=org
cn: APP_MDL_ACL_RO_herd_ro
objectClass: top
objectClass: groupOfNames
member: cn=ro_user,ou=People,dc=mdl,dc=org
member: cn=basic_user,ou=People,dc=mdl,dc=org

# APP_MDL_ACL_RO_mdl_rw, Groups, mdl.org
dn: cn=APP_MDL_ACL_RO_mdl_rw,ou=Groups,dc=mdl,dc=org
cn: APP_MDL_ACL_RO_mdl_rw
objectClass: top
objectClass: groupOfNames
member: cn=mdl_user,ou=People,dc=mdl,dc=org
member: cn=admin_user,ou=People,dc=mdl,dc=org

# APP_MDL_ACL_RO_sec_market_data_rw, Groups, mdl.org
dn: cn=APP_MDL_ACL_RO_sec_market_data_rw,ou=Groups,dc=mdl,dc=org
cn: APP_MDL_ACL_RO_sec_market_data_rw
objectClass: top
objectClass: groupOfNames
member: cn=sec_user,ou=People,dc=mdl,dc=org
member: cn=admin_user,ou=People,dc=mdl,dc=org

# APP_MDL_Users, Groups, mdl.org
dn: cn=APP_MDL_Users,ou=Groups,dc=mdl,dc=org
cn: APP_MDL_Users
objectClass: top
objectClass: groupOfNames
member: cn=mdl_user,ou=People,dc=mdl,dc=org
member: cn=sec_user,ou=People,dc=mdl,dc=org
member: cn=ro_user,ou=People,dc=mdl,dc=org
member: cn=admin_user,ou=People,dc=mdl,dc=org

# search result
search: 2
result: 0 Success

# numResponses: 15
# numEntries: 14
```
  
## How to find Herd-MDL logs in CloudWatch
Note: Logs are created in cloudwatch only when the log content is NOT empty; therefore, some of the log streams mentioned below may not be found if there is no content in the logs.

### Logs inside customized stack log group

**Steps:**

*   Login to the AWS Console and navigate to CloudWatch.
*   Click on 'Logs' in the left panel.
*   Filter Log Groups with your stack's cloudwatch log-group name.
    *   **where to find the stack's log group name?**: it's the value for the parameter: _CloudWatchLogGroupName_ in the outputs section of the MDL stack.(Example value: `logtest-MdlStack-10IBXFGDHF94M`)
*   Click on above filtered stack log group to access it: inside this stack log group folder, you will find the logs saved as individual log streams.

**Elastic Search Log Streams:**

|   |   |
| ----- | ----- |
| **Description** | **Location(format)**
| CodeDeploy Logs | elasticsearch/codedeploy/*
| Apache Logs | elasticsearch/apache/*
| Elastic Search Logs | elasticsearch/*
  
**Herd Log Streams:**

|   |   |
| ----- | ----- |
| **Description** | **Location(format)**
| **CodeDeploy Log** | herd/codedeploy/*
| **Apache Logs** | herd/apache/*
| **Tomcat Logs** | herd/tomcat/*
| **Application Logs** | herd/application/herd.log

**Metastor Log Streams:**

|   |   |
| ----- | ----- |
| **Description** | **Location(format)**
| **CodeDeploy Logs** | metastor/codedeploy/*


**Bdsql Log Streams:**

|   |   |
| ----- | ----- |
| **Description** | **Location(format)**
| **EMR bootstrap Logs** | /bdsql/bootstrap/*
| **EMR hadoop step Logs** | /bdsql/hadoop/step/*

**OpenLdap Log Streams:**

|   |   |
| ----- | ----- |
| **Description** | **Location(format)**
| **CodeDeploy Log** | openldap/codedeploy/*


### Logs with aws default log group

**Rds Log Group:**

|   |   |   |
| ----- | ----- | ----- |
| **Description** | **Location(format)** | *Example* |
| **Rds Log** | /aws/rds/instance/{{RdsInstanceName}}/{{logType}}/{{RdsInstanceName}} |  <table><tr><td>/aws/rds/instance/logtest-prod-metastor/error</td></tr><tr><td>/aws/rds/instance/logtest-prod-metastor/general</td></tr><td>/aws/rds/instance/logtest-prod-metastor/audit</td></tr><tr><td>/aws/rds/instance/logtest-prod-metastor/slowquery</td></tr></table>|


**Lambda Log Group:**

|   |   |   |
| ----- | ----- | ----- |
| **Description** | **Location(format)** | *Example* |
| **Lambda Log** | /aws/lambda/{{lambda_function_name}}	| /aws/lambda/maggietest-ArtifactCopyLambdaFunction-IT8KBCH3IFQ4 |


## HERD zero-downtime upgrade

MDL offers a Blue/Green style rolling upgrade mechanism for HERD and guarantees the following:

  * New Herd version (App, DB) is active with one click
  * All Herd data is unchanged including metadata catalog and Herd configuration
  * Works with zero downtime
  * Upgrade is a blue/green style deployment and the relacement stack is smoke-tested before traffic is rerouted.
  * 

> Note: Rolling upgrades currently support forward-compatibility only, this means that Herd can only be upgraded to a *newer* version. 

*How to perform a rolling upgrade*

MDL includes a [Lambda function](https://aws.amazon.com/lambda/) which takes care of managing configurations and invoking a zero-downtime upgrade to Herd, 
below are steps on how to use the aforementioned Lambda function to perform a rolling upgrade:

1. Login to the AWS console and find the Upgrade-utility lambda function, it is named in the following format: 
   <br> _${MDLInstanceName}-Herd-${Environment}-UPGRADE-HERD-LAMBDA-FUNCTION_
   <br> eg. `MDL-Herd-dev-UPGRADE-HERD-LAMBDA-FUNCTION`

2. Invoke the lambda function with the following event (payload):
```json
    {
      "RequestedVersion": "<desired-herd-version>"
    }
```

The lambda function can be invoked in the following ways:

  * Using the AWS CLI's [invoke-lambda](https://docs.aws.amazon.com/cli/latest/reference/lambda/invoke.html) command.
  * From the AWS Lambda console, [manually invoke the Lambda function](https://docs.aws.amazon.com/lambda/latest/dg/getting-started-create-function.html#get-started-invoke-manually) using a test-event.
  * Using the [AWS-SDK](https://aws.amazon.com/tools/). 
  
  <div class="admonition important">
  <p class="admonition-title">Try it out!</p>
  <p>
  <b> 
  To try out a zero-downtime deployment, check out our [walk-through](/tutorials/#performing-a-zero-downtime-upgrade-on-herd) tutorial which will guide you through a sample deployment using the AWS Lambda console.
  </b>
  <p>  
  </div>
  
## Troubleshooting


*Problem*

Cloud formation fails with error "The maximum number of addresses has been reached". This happens while creating VPC.

*Cause*

The account has reached the maximum number of EIPs. EIPs are needed while configuring VPC for NatGateway.

*Solution*

1.  Recreate the MDL stack with option "VPC=false". In this case, user needs to create SSM parameters as per "CreateVPC" section in [Herd-MDL CFT Specifications](advanced-install.md/#conditional-parameters)   
2.  Increase the account level limit for EIPs so that NatGateway doesn't fail while allocating EIP in the account

----

*Problem*

Cloud formation fails with error "Already exists in stack". This happens while creating AWS resources.

*Cause*

There is already another stack with the same MDLInstanceName in the same AWS account in the same region . 

*Solution*

1.  Delete the existing stack with same "MDLInstanceName" and then re-try creating the new stack
2.  Enter different "MDLInstanceName" for the new stack so that this does not collide with existing resources

----

*Problem*

Code Deploy fails with error "HEALTH_CONSTRAINTS". This happens with ElasticSearch/Herd/Metastor EC2 instances.

*Cause*

This seems to be an AWS issue that occurs occasionally. Under certain cases, CodeDeploy is unable to find the EC2 Instances created by AutoScaling group. 

*Solution*

1.  Restart the Stack Creation process   

## Known Issues

1.  Emptying S3 buckets manually before deleting the stack

S3 buckets need to be emptied before deleting the stack. Otherwise, stack deletion fails. This will be fixed in future releases such that MDL takes care of emptying the buckets when Stack Deletion is triggered.

2. Deleting SSM parameters manually before deleting the stack

Some of the SSM parameters need to be deleted before deleting the stack. Note that Stack Deletion will not fail. However, these SSM parameters will be stale, if they are not cleaned-up. Search for all the SSM parameters with "MDLInstanceName" and delete the same for clean-up. This will be fixed in future releases such that MDL takes care of deleting all the SSM parameters, when Stack Deletion is triggered.

3. Deleting RDS Snapshots

AWS account has limits for number of RDS snapshots stored. MDL by default stores the RDS snapshot before deleting the same. If the number of RDS snapshots reach the AWS account limit, then MDL cannot delete the stack due to RDS snapshot issue. In this case, the user either needs to clean-up existing RDS snapshots or increase the RDS snapshot limit for the AWS account. 

  