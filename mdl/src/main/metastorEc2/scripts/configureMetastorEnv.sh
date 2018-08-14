#
# Copyright 2018 herd-mdl contributors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#!/bin/bash
echo "$@"

# Check the error and fail if the last command is NOT successful
function check_error {
    return_code=${1}
    cmd="$2"
    if [ ${return_code} -ne 0 ]
    then
        echo "$(date "+%m/%d/%Y %H:%M:%S") *** ERROR *** ${cmd} has failed with error $return_code"
        exit 1
    fi
}

# Execute the given command and support resume option
function execute_cmd {
        cmd="${1}"
            echo $cmd
        eval $cmd
        check_error ${PIPESTATUS[0]} "$cmd"
}

#MAIN
configFile="/home/mdladmin/deploy/mdl/conf/deploy.props"
if [ ! -f ${configFile} ] ; then
    echo "Config file does not exist ${configFile}"
    exit 1
fi
. ${configFile}

herdAdminUsername=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/User/HerdAdminUsername --region ${region} --output text --query Parameter.Value)
herdAdminPassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/Password/HerdAdminPassword --with-decryption --region ${region} --output text --query Parameter.Value)

# Curl commands need to be retried multiple times to avoid network errors
function execute_curl_cmd {
	cmd="${1} --retry 5 --max-time 120 --retry-delay 7 --write-out \"\nHTTP_CODE:%{http_code}\n\" -u ${herdAdminUsername}:${herdAdminPassword}"
	echo "${1} --retry 5 --max-time 120 --retry-delay 7 --write-out \"\nHTTP_CODE:%{http_code}\n\" "
	eval $cmd > /tmp/curlCmdOutput 2>&1
	returnCode=`cat /tmp/curlCmdOutput | grep "HTTP_CODE" | cut -d":" -f2`
    if [ "${returnCode}" != "200"                                                                                                                                                                                         ]; then
        echo "$(date "+%m/%d/%Y %H:%M:%S") *** ERROR *** ${1} has failed with error ${returnCode}"
    	cat /tmp/curlCmdOutput
    	echo ""
        exit 1
    fi
}

metastorDBPassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/RDS/hiveAccount --with-decryption --region ${region} --output text --query Parameter.Value)

if [ "${refreshDatabase}" = "true" ] ; then

    # addPartitionWorkflow
    execute_cmd "sed -i \"s/{{STAGING_BUCKET_ID}}/s3\:\/\/${mdlStagingBucketName}/g\" ${deployLocation}/managedObjectLoader/workflow-def/addPartitionWorkflow.xml"
    execute_cmd "sed -i \"s/{{RDS_HOST}}/${metastorDBHost}/g\" ${deployLocation}/managedObjectLoader/workflow-def/addPartitionWorkflow.xml"
    execute_cmd "sed -i \"s/{{NAMESPACE}}/MDL/g\" ${deployLocation}/managedObjectLoader/workflow-def/addPartitionWorkflow.xml"
    execute_cmd "sed -i \"s/{{CLUTER_NAME}}/${mdlInstanceName}_Cluster/g\" ${deployLocation}/managedObjectLoader/workflow-def/addPartitionWorkflow.xml"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/managedObjectLoader/workflow-def/addPartitionWorkflow.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/jobDefinitions --insecure"

    # Replace values for cluster definition
    execute_cmd "sed -i \"s/{{NAMESPACE}}/MDL/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{DEFAULT_CLUSTER_DEF}}/MDLMetastorHiveCluster/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{MYSQL_RDS}}/${metastorDBHost}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{MDL_INSTANCE_NAME}}/${mdlInstanceName}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{REGION}}/${region}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{ENVIRONMENT}}/${environment}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{DEPLOY_BUCKET_KEY}}/${releaseVersion}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{S3_METASTOR_BUCKET}}/s3\:\/\/${mdlStagingBucketName}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{S3_DEPLOY_BUCKET}}/${deploymentBucketName}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{S3_DEPLOY_BUCKET_BOOTSTRAP}}/s3\:\/\/${deploymentBucketName}\/${releaseVersion}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{EMR_METASTOR_SR}}/${mdlEMRServiceRole}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{SSH_KEY_PAIR}}/${metastorHiveClusterKeyName}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{PRIVATE_SUBNETS}}/${privateSubnets}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{IAM_PROFILE}}/${mdlInstanceProfile}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{SG_METASTOR_EMRMSTR_ID}}/${metastorEMRSecurityGroup}/g\" ${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml"
    execute_cmd "aws s3 cp ${configFile} s3://${mdlStagingBucketName}/deploy/metastor/deploy.props"

    # Register cluster definition
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/metastoreOperations/samples/emr-cluster/metastoreHiveClusterDefinition.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/emrClusterDefinitions --insecure"

    # Smoke Testing setup
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/smokeTesting/dataProvider.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/dataProviders --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/smokeTesting/objectRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/businessObjectDefinitions --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/smokeTesting/formatRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/businessObjectFormats --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/smokeTesting/objectNotificationRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/notificationRegistrations/businessObjectDataNotificationRegistrations --insecure"

fi

# Change the db.properties
execute_cmd "sed -i \"s/{{MYSQL_RDS}}/${metastorDBHost}/g\" ${deployLocation}/metastoreOperations/rds/conf/db.properties"
sed -i "s/{{MYSQL_PASSWORD}}/${metastorDBPassword}/g" ${deployLocation}/metastoreOperations/rds/conf/db.properties
check_error $? "sed -i MYSQL_PASSWORD ${deployLocation}/metastoreOperations/rds/conf/db.properties"
execute_cmd "aws s3 cp ${deployLocation}/metastoreOperations/rds/conf/db.properties s3://${mdlStagingBucketName}/deploy/metastor/db.properties"

# Upload the data
execute_cmd "cd ${deployLocation}/data"
execute_cmd "sed -i \"s/{{mdlInstanceName}}/${mdlInstanceName}/g\" ${deployLocation}/data/smokeTestingDataFile.txt"
execute_cmd "echo 3\|${mdlInstanceName}-$(date) >> ${deployLocation}/data/smokeTestingDataFile.txt"

# Set the port and SSL information for uploader based on https or http
if [ "${httpProtocol}" = "https" ] ; then
export port="-P 443 -s true"
else
    export port="-P 80"
fi

# Download herd-uploader jar
execute_cmd "mkdir -p ${deployLocation}/herd-uploader"
#TODO remove hardcoded uploader-jar version
execute_cmd "wget --quiet --random-wait http://central.maven.org/maven2/org/finra/herd/herd-uploader/0.72.0/herd-uploader-0.72.0.jar -O ${deployLocation}/herd-uploader/herd-uploader-app.jar"
execute_cmd "sudo chmod +x ${deployLocation}/herd-uploader/herd-uploader-app.jar"

# Do not echo the password
echo "java -jar ${deployLocation}/herd-uploader/herd-uploader-app.jar --force -l ${deployLocation}/data -m ${deployLocation}/conf/metaFile -V -H ${herdLoadBalancerDNSName} ${port} --disableHostnameVerification true"
eval "java -jar ${deployLocation}/herd-uploader/herd-uploader-app.jar --force -l ${deployLocation}/data -m ${deployLocation}/conf/metaFile -V -H ${herdLoadBalancerDNSName} ${port} -u ${herdAdminUsername} -w ${herdAdminPassword} --disableHostnameVerification true"
check_error ${PIPESTATUS[0]} "java -jar ${deployLocation}/herd-uploader/herd-uploader-app.jar --force -l ${deployLocation}/data -m ${deployLocation}/conf/metaFile -V -H ${herdLoadBalancerDNSName} --disableHostnameVerification true"

# Execute the demo script if needed
if [ "${createDemoObjects}" = "true" ] ; then
    execute_cmd "${deployLocation}/scripts/demoMetastor.sh"
fi

# Signal success to the CloudFormation Stack
execute_cmd "/opt/aws/bin/cfn-signal -e 0 -r 'Metastor Creation Complete' \"${waitHandleForMetastor}\""

exit 0
