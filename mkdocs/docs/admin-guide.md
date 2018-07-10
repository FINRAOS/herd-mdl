Herd-MDL Administration Guide
========================

## How To Login to EC2 Instance of MDL

**Prerequisite**

*   AWS Console Access of the AWS Account, where MDL is created
    
*   SSH Client (Example Putty)
*   MDL Instance Name of the MDL stack
    *   This is the parameter to the MDL Cloudformation Stack
*   Bastion Host in the VPC in case of connectivity issues to the private subnet
  
**Steps**

*   Login to AWS Console and navigate to SSM Parameter section (Refer [AWS Documentation](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-console.html))
*   Open Parameter /app/MDL/${MDLInstanceName}/${Environment}/KEYS/KeypairName
    *   Example : /app/MDL/mdl/dev/KEYS/KeypairName
*   Get the Value for the above parameter. That value specifies the keyName which holds the pem file
    *   Example: app\_mdl\_dev
*   Open Parameter "app\_mdl\_dev" - Value from the previous step
    *   The value of this parameter is a SecureString and that is the PEM file for the node
*   Login to the node using SSH client with user name "ec2-user" and the PEM file from previous step
    *   Default AMI has ec2-user configuration

## How To Find MDL User Credentials to Login to Herd/Shepherd/Bdsql

This section describes how to locate credentials required for endpoints when you have installed with EnableSSLAndAuth=true. 

**Prerequisites**

*   AWS Console Access of the AWS Account, where MDL is created
*   MDL Instance Name of the MDL stack
    *   This is the parameter to the MDL Cloudformation Stack
*   EnableSSLAndAuth must be set to true while creating the stack

**Steps**

*   Login to AWS Console and navigate to SSM Parameter section (Refer [AWS Documentation](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-console.html))
*   User Name
    *   Open Parameter /app/MDL/${MDLInstanceName}/${Environment}/LDAP/MdlAppUsername
        *   Example :  /app/MDL/mdl/dev/LDAP/MdlAppUsername
    *   Get the Value for the above parameter. That value specifies the user name for Herd/Bdsql/Shepherd
        *   Example: ldap\_mdl\_app_user
*   Password  
    *   Open Parameter /app/MDL/${MDLInstanceName}/${Environment}/LDAP/MDLAppPassword
        *   Example :  /app/MDL/mdl/dev/LDAP/MDLAppPassword
    *   Get the Value for the above parameter. That value specifies the password for Herd/Bdsql/Shepherd, and this parameter is a Secure String
        *   Example: ODMyOTdmZmE5
*   Use the above User name, and Password to login to Herd/Shepherd/Bdsql  


## How Tos for initial creation of Herd metadata 


Refer to [Herd Documentation](https://github.com/FINRAOS/herd/wiki/quick-start-to-registering-data#create-a-new-storage-instance) for details on the following:
*  Add Storage in Herd
*  Create New Namespace in Herd
*  Create New Business Object in Herd
*  Create New Format in Herd
*  Register Data in Herd

## How To Add the Object in Herd to Metastor

Once Business Object is created in Herd, there is an additional step to include the Objet in Metastor so that the Object is query-able in BDSQL. This step registers a Notification for the Object such that an Activity workflow is triggered for every Data Registration to this Object. This is a one time step for the Object. Once Object is registered for the Notification, all the future partitions registered for the Object will be available in BDSQL.

Here is the XML to add the Notification in Herd

**objectNotification.xml**
```
<businessObjectDataNotificationRegistrationCreateRequest>
    <businessObjectDataNotificationRegistrationKey>
        <namespace>MDL</namespace>
        <notificationName>METASTOR_{{NAMESPACE}}_{{USAGE}}_{{OBJECT\_NAME}}\_{{FILE_FORMAT}}</notificationName>
    </businessObjectDataNotificationRegistrationKey>
    <businessObjectDataEventType>BUS\_OBJCT\_DATA\_STTS\_CHG</businessObjectDataEventType>
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
cat objectNotification.xml | curl -H "Content-Type: application/xml" -d @- -X POST http://HERD\_DNS\_NAME/herd-app/rest/notificationRegistrations/businessObjectDataNotificationRegistrations | xmllint --format -
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
        *   Open Parameter /app/MDL/${MDLInstanceName}/${Environment}/LDAP/MdlAppUsername
        *   Example :  /app/MDL/mdl/dev/LDAP/MdlAppUsername
        *   Get the Value for the above parameter. That value specifies the user name for Herd/Bdsql/Shepherd
        *   Example: ldap\_mdl\_app_user
    *   Password  
        *   Open Parameter /app/MDL/${MDLInstanceName}/${Environment}/LDAP/MDLAppPassword
        *   Example :  /app/MDL/mdl/dev/LDAP/MDLAppPassword
        *   Get the Value for the above parameter. That value specifies the password for Herd/Bdsql/Shepherd, and this parameter is a Secure String
        *   Example: ODMyOTdmZmE5
    *   Use the above URL, User name, and Password to login to Bdsql using the SQL Client that supports Presto JDBC Driver

## How To Manage OpenLDAP Users & Groups

MDL provides a helper script to manage users and groups. The script is deployed onto the OpenLDAP server in /home/mdladmin/deploy/mdl/scripts/.

**Usage and examples**

\# ./manageLdap.sh --help

Usage:
```manageLdap.sh --action \[create\_user|create\_group|add\_user\_to\_group|show\_directory\] \[--user name\] \[--group name\] \[--dn distinguishedname\]```

Examples:
```
manageLdap.sh --action create_user --user userA
manageLdap.sh --action create_group --user userA --group groupA
manageLdap.sh --action add\_user\_to_group --user userB --group groupA
manageLdap.sh --action delete_object --dn "cn=userA,ou=People,ou=Groups,cn=domain,cn=com"
manageLdap.sh --action show_directory
manageLdap.sh --help
```

**Create new user**

```\# ./manageLdap.sh --action create_user --user userA```

adding new entry "uid=userA,ou=People,dc=finra,dc=org"

**Create new group**

```\# ./manageLdap.sh --action create_group --user userA --group groupA```

adding new entry "cn=groupA,ou=Groups,dc=finra,dc=org"

**Add user to group**

```\# ./manageLdap.sh --action add\_user\_to_group --user userB --group groupA```

modifying entry "cn=groupA,ou=Groups,dc=finra,dc=org"

**Delete object**

```\# ./manageLdap.sh --action delete_object --dn "uid=userA,ou=People,dc=finra,dc=org"```

**Show directory information**

```
\# ./manageLdap.sh --action show_directory
\# extended LDIF
#
\# LDAPv3
\# base <dc=finra,dc=org> with scope subtree
\# filter: (objectclass=*)
\# requesting: ALL
#
```

```
\# finra.org
dn: dc=finra,dc=org
objectClass: dcObject
objectClass: organization
o: finra.org
dc: finra

\# People, finra.org
dn: ou=People,dc=finra,dc=org
objectClass: top
objectClass: organizationalUnit
ou: People

\# Groups, finra.org
dn: ou=Groups,dc=finra,dc=org
objectClass: top
objectClass: organizationalUnit
ou: Groups

\# ldap\_mdl\_app_user, People, finra.org
dn: uid=ldap\_mdl\_app_user,ou=People,dc=finra,dc=org
uid: ldap\_mdl\_app_user
cn: ldap\_mdl\_app_user
sn: null
objectClass: inetOrgPerson
userPassword:: e1NTSEF9SXUwdXJHdXl1cnVkSFFISE4wbmRQZU05ZEkrRiszYng=

\# ldap\_sec\_app_user, People, finra.org
dn: uid=ldap\_sec\_app_user,ou=People,dc=finra,dc=org
uid: ldap\_sec\_app_user
cn: ldap\_sec\_app_user
sn: null
objectClass: inetOrgPerson
userPassword:: e1NTSEF9UlVGVGoxVnJ2c3dVemxZdDlvRHltTXNXMnByaDFlWGo=

\# APP\_MDL\_Users, Groups, finra.org
dn: cn=APP\_MDL\_Users,ou=Groups,dc=finra,dc=org
objectClass: top
objectClass: groupOfNames
member: uid=ldap\_mdl\_app_user,ou=People,dc=finra,dc=org
cn: APP\_MDL\_Users

\# userB, People, finra.org
dn: uid=userB,ou=People,dc=finra,dc=org
uid: userB
cn: userB
sn: null
objectClass: inetOrgPerson
userPassword:: e1NTSEF9bVZJTGJLcTk4THNmSFVSeXhyQ3d5U0Qybm0xQnNMY0E=

\# userA, People, finra.org
dn: uid=userA,ou=People,dc=finra,dc=org
uid: userA
cn: userA
sn: null
objectClass: inetOrgPerson
userPassword:: e1NTSEF9WlpFeHhaL3pFZmZ0dUJJbWdoSkJkUy9aV0hEejVYY2g=

\# groupA, Groups, finra.org
dn: cn=groupA,ou=Groups,dc=finra,dc=org
objectClass: top
objectClass: groupOfNames
member: uid=userA,ou=People,dc=finra,dc=org
member: uid=userB,ou=People,dc=finra,dc=org
cn: groupA

\# search result
search: 2
result: 0 Success

\# numResponses: 10
\# numEntries: 9
```
  
## How to find Herd-MDL logs in CloudWatch
Note:Logs are created in cloudwatch only when the log content is not empty, therefore, some of the log streams mentioned below may not be found if the logs are empty.

$StackLogGroupName:
the stack name of MDL - Installation template (eg: maggieteststack-MdlStack-XY4EUHA50KVL).

**Elastic Search Logs:**

$EsEc2InstanceId: the instance id of elasticsearch ec2

*   CodeDeploy Logs: 
    *  $StackLogGroupName/elasticsearch/codedeploy/$EsEc2InstanceId-codedeploy-agent-log
    *  $StackLogGroupName/elasticsearch/codedeploy/$EsEc2InstanceId-deployments-log
    *  $StackLogGroupName/elasticsearch/codedeploy/$EsEc2InstanceId-updater-log
 
*   Apache Logs:
    *  $StackLogGroupName/elasticsearch/apache/$EsEc2InstanceId-apache-log
    *  $StackLogGroupName/elasticsearch/apache/$EsEc2InstanceId-apache-access-log
    *  $StackLogGroupName/elasticsearch/apache/$EsEc2InstanceId-apache-error-log
    *  $StackLogGroupName/elasticsearch/apache/$EsEc2InstanceId-apache-ssl-access-log
    *  $StackLogGroupName/elasticsearch/apache/$EsEc2InstanceId-apache-ssl-error-log
    *  $StackLogGroupName/elasticsearch/apache/$EsEc2InstanceId-apache-ssl-request-log
  
* Elastic Search Logs:
    * $StackLogGroupName/elasticsearch/$EsEc2InstanceId-elasticsearch-log
    * $StackLogGroupName/elasticsearch/$EsEc2InstanceId-elasticsearch-index_indexing_slowlog-log
    * $StackLogGroupName/elasticsearch/$EsEc2InstanceId-elasticsearch-index_search_slowlog-log
    * $StackLogGroupName/elasticsearch/$EsEc2InstanceId-elasticsearch_deprecation-log

**Herd Logs:**

$HerdEc2InstanceId: the instance id of herd ec2

* CodeDeploy Logs: 
     * $StackLogGroupName/herd/codedeploy/$HerdEc2InstanceId-codedeploy-agent-log
     * $StackLogGroupName/herd/codedeploy/$HerdEc2InstanceId-deployments-log
     * $StackLogGroupName/herd/codedeploy/$HerdEc2InstanceId-updater-log

* Apache Logs:
    * $StackLogGroupName/herd/apache/$HerdEc2InstanceId-apache-access-log
    * $StackLogGroupName/herd/apache/$HerdEc2InstanceId-apache-error-log

* Tomcat Logs:
    * $StackLogGroupName/herd/tomcat/$HerdEc2InstanceId-tomcat-catalina-log
    * $StackLogGroupName/herd/tomcat/$HerdEc2InstanceId-tomcat-catalina-out-log
    * $StackLogGroupName/herd/tomcat/$HerdEc2InstanceId-tomcat-localhost-access-log
    * $StackLogGroupName/herd/tomcat/$HerdEc2InstanceId-tomcat-localhost-log

**Metastor Logs:**

$MetastorEc2InstanceId: the instance id of metastor ec2

* CodeDeploy Logs: 
    * $StackLogGroupName/metastor/codedeploy/$MetastorEc2InstanceId-codedeploy-agent-log
    * $StackLogGroupName/metastor/codedeploy/$MetastorEc2InstanceId-deployments-log
    * $StackLogGroupName/metastor/codedeploy/$MetastorEc2InstanceId-updater-log

* Rds Logs: 
    * Error Log: /aws/rds/instance/$RdsInstanceName/error/$RdsInstanceName
    * General Log: /aws/rds/instance/$RdsInstanceName/general/$RdsInstanceName
    * Audit Log: /aws/rds/instance/$RdsInstanceName/audit/$RdsInstanceName
    * Slow query Log: /aws/rds/instance/$RdsInstanceName/slowquery/$RdsInstanceName

**Bdsql Logs:**

$BdsqlEc2InstanceId: the instance id of bdsql ec2

* EMR bootstrap Logs:
    * $StackLogGroupName/bdsql/bootstrap/$BdsqlEc2InstanceId-controller
    * $StackLogGroupName/bdsql/bootstrap/$BdsqlEc2InstanceId-stderr
    * $StackLogGroupName/bdsql/bootstrap/$BdsqlEc2InstanceId-stdout

* EMR hadoop step Logs:
    * $StackLogGroupName/bdsql/hadoop/steps/$BdsqlEc2InstanceId-controller
    * $StackLogGroupName/bdsql/hadoop/steps/$BdsqlEc2InstanceId-stderr
    * $StackLogGroupName/bdsql/hadoop/steps/$BdsqlEc2InstanceId-stdout
    
**OpenLdap Logs:**

$OpenLdapEc2InstanceId: the instance id of OpenLdap ec2

* CodeDeploy Logs: 
    * $StackLogGroupName/openldap/codedeploy/$OpenLdapEc2InstanceId-codedeploy-agent-log
    * $StackLogGroupName/openldap/codedeploy/$OpenLdapEc2InstanceId-deployments-log
    * $StackLogGroupName/openldap/codedeploy/$OpenLdapEc2InstanceId-updater-log

**Lambda Logs:**
/aws/lambda/$lambda_function_name/(eg:/aws/lambda/maggietest-ArtifactCopyLambdaFunction-IT8KBCH3IFQ4)

## Troubleshooting

----

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

There is already another stack with the same MDLInstanceName in the AWS account. 

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

  