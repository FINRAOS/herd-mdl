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

# Curl commands need to be retried multiple times to avoid network errors
function execute_curl_cmd {
	cmd="${1} --retry 5 --max-time 120 --retry-delay 7 --write-out \"\nHTTP_CODE:%{http_code}\n\" -u ${ldapMdlAppUsername}:${mdlUserLdapPassword}"
	echo "${1} --retry 5 --max-time 120 --retry-delay 7 --write-out \"\nHTTP_CODE:%{http_code}\n\" "
	eval $cmd > /tmp/curlCmdOutput 2>&1
	returnCode=`cat /tmp/curlCmdOutput | grep "HTTP_CODE" | cut -d":" -f2`
    if [ "${returnCode}" != "200" ]; then
        echo "$(date "+%m/%d/%Y %H:%M:%S") *** ERROR *** ${1} has failed with error ${returnCode}"
    	cat /tmp/curlCmdOutput
    	echo ""
        exit 1
    fi
}

#MAIN
configFile="/home/mdladmin/deploy/mdl/conf/deploy.props"
if [ ! -f ${configFile} ] ; then
    echo "Config file does not exist ${configFile}"
    exit 1
fi
. ${configFile}

execute_cmd "echo \"From $0\""
mdlUserLdapPassword=$(aws ssm get-parameter --name ${ldapMdlAppUserPasswordParameterKey} --with-decryption --region ${region} --output text --query Parameter.Value)

metastorDBPassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/RDS/hiveAccount --with-decryption --region ${region} --output text --query Parameter.Value)

if [ "${refreshDatabase}" = "true" ] ; then

    execute_cmd "sed -i \"s/{{STAGING_BUCKET_ID}}/s3\:\/\/${mdlStagingBucketName}/g\" ${deployLocation}/xml/install/addPartitionWorkflow.xml"
    execute_cmd "sed -i \"s/{{RDS_HOST}}/${metastorDBHost}/g\" ${deployLocation}/xml/install/addPartitionWorkflow.xml"
    execute_cmd "sed -i \"s/{{CLUTER_NAME}}/${mdlInstanceName}_Cluster/g\" ${deployLocation}/xml/install/addPartitionWorkflow.xml"

    # addPartitionWorkflow
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/install/namespaceRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/namespaces --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/install/addPartitionWorkflow.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/jobDefinitions --insecure"

    # Replace values for cluster definition
    execute_cmd "sed -i \"s/{{MYSQL_RDS}}/${metastorDBHost}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{MDL_INSTANCE_NAME}}/${mdlInstanceName}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{REGION}}/${region}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{ENVIRONMENT}}/${environment}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{DEPLOY_BUCKET_KEY}}/${releaseVersion}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{S3_METASTOR_BUCKET}}/s3\:\/\/${mdlStagingBucketName}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{S3_DEPLOY_BUCKET}}/${deploymentBucketName}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{S3_DEPLOY_BUCKET_BOOTSTRAP}}/s3\:\/\/${deploymentBucketName}\/${releaseVersion}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{EMR_METASTOR_SR}}/${mdlEMRServiceRole}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{SSH_KEY_PAIR}}/${metastorHiveClusterKeyName}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{PRIVATE_SUBNETS}}/${privateSubnets}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{IAM_PROFILE}}/${mdlInstanceProfile}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "sed -i \"s/{{SG_METASTOR_EMRMSTR_ID}}/${metastorEMRSecurityGroup}/g\" ${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml"
    execute_cmd "aws s3 cp ${configFile} s3://${mdlStagingBucketName}/deploy/metastor/deploy.props"

    # Register cluster definition
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/install/metastoreHiveClusterDefinition.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/emrClusterDefinitions --insecure"

    # Smoke Testing setup
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/smokeTesting/dataProvider.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/dataProviders --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/smokeTesting/objectRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/businessObjectDefinitions --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/smokeTesting/formatRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/businessObjectFormats --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/smokeTesting/objectNotificationRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/notificationRegistrations/businessObjectDataNotificationRegistrations --insecure"

fi

# Change the db.properties
execute_cmd "sed -i \"s/{{MYSQL_RDS}}/${metastorDBHost}/g\" ${deployLocation}/xml/install/hive-site.xml"
execute_cmd "aws s3 cp ${deployLocation}/xml/install/hive-site.xml s3://${mdlStagingBucketName}/deploy/metastor/hive-site.xml"
execute_cmd "sed -i \"s/{{MYSQL_RDS}}/${metastorDBHost}/g\" ${deployLocation}/conf/db.properties"
sed -i "s/{{MYSQL_PASSWORD}}/${metastorDBPassword}/g" ${deployLocation}/conf/db.properties
check_error $? "sed -i MYSQL_PASSWORD ${deployLocation}/conf/db.properties"
execute_cmd "aws s3 cp ${deployLocation}/conf/db.properties s3://${mdlStagingBucketName}/deploy/metastor/db.properties"

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

# Do not echo the password
echo "java -jar ../jenkins/herd-uploader-app.jar --force -l ${deployLocation}/data -m ${deployLocation}/conf/metaFile -V -H ${herdLoadBalancerDNSName} ${port}"
eval "java -jar ../jenkins/herd-uploader-app.jar --force -l ${deployLocation}/data -m ${deployLocation}/conf/metaFile -V -H ${herdLoadBalancerDNSName} ${port} -u ${ldapMdlAppUsername} -w ${mdlUserLdapPassword}"
check_error ${PIPESTATUS[0]} "java -jar ../jenkins/herd-uploader-app.jar --force -l ${deployLocation}/data -m ${deployLocation}/conf/metaFile -V -H ${herdLoadBalancerDNSName}"

# Installing Shepherd
# Should we make Shepherd bucket configurable?
execute_cmd "mkdir -p ${deployLocation}/jenkins/shepherd/tmp"
execute_cmd "unzip ${deployLocation}/jenkins/shepherd.zip -d ${deployLocation}/jenkins/shepherd/tmp"
execute_cmd "aws s3 sync ${deployLocation}/jenkins/shepherd/tmp s3://${shepherdS3BucketName}"

# Change Shepherd based on authentication selection
if [ "${enableSSLAndAuth}" = "true" ] ; then
    execute_cmd "sed -i \"s/{{USE_BASIC_AUTH}}/true/g\" ${deployLocation}/conf/configuration.json"
    execute_cmd "sed -i \"s/{{BASIC_AUTH_REST_UI}}/${httpProtocol}\:\/\/${herdLoadBalancerDNSName}\/herd-app\/rest/g\" ${deployLocation}/conf/configuration.json"
    execute_cmd "sed -i \"s/{{HERD_URL}}/${httpProtocol}:\/\/${mdlInstanceName}herd.${domainNameSuffix}/g\" ${deployLocation}/conf/configuration.json"
else
    execute_cmd "sed -i \"s/{{USE_BASIC_AUTH}}/false/g\" ${deployLocation}/conf/configuration.json"
    execute_cmd "sed -i \"s/{{BASIC_AUTH_REST_UI}}//g\" ${deployLocation}/conf/configuration.json"
    execute_cmd "sed -i \"s/{{HERD_URL}}/${httpProtocol}:\/\/${herdLoadBalancerDNSName}/g\" ${deployLocation}/conf/configuration.json"
fi
execute_cmd "aws s3 cp ${deployLocation}/conf/configuration.json s3://${shepherdS3BucketName}"

# Execute the demo script if needed
if [ "${createDemoObjects}" = "true" ] ; then
    execute_cmd "${deployLocation}/scripts/demoMetastor.sh"
fi

# Smoke test by requesting index.html and making sure its working
execute_cmd "curl ${shepherdWebSiteBucketUrl}/index.html"


# Signal to Cloud Stack
execute_cmd "/opt/aws/bin/cfn-signal -e 0 -r 'Metastor Creation Complete' \"${waitHandleForMetastor}\""



exit 0
