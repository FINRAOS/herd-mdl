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

# Get the deploy properties
deployProps="/home/hadoop/conf/deploy.props"
. ${deployProps}
deployLocation="/home/hadoop"
execute_cmd "aws configure set default.region ${region}"

hivePassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/HIVE/hiveAccount --with-decryption --region ${region} --output text --query Parameter.Value)
herdAdminPassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/Password/HerdAdminPassword --with-decryption --region ${region} --output text --query Parameter.Value)
herdAdminUsername=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/User/HerdAdminUsername --region ${region} --output text --query Parameter.Value)

execute_cmd "wget --quiet --random-wait https://github.com/FINRAOS/herd-mdl/releases/download/metastor-v${metastorVersion}/managedObjectLoader-${metastorVersion}-dist.zip -O ${deployLocation}/managedObjectLoader.zip"

execute_cmd "cd ${deployLocation}"
execute_cmd "unzip managedObjectLoader.zip"

config_file_location="${deployLocation}/managedObjectLoader/scripts/config/application.properties"

# Change the application.properties for metastor
execute_cmd "sed -i \"s/{{DM_REST_URL}}/${httpProtocol}:\/\/${herdLoadBalancerDNSName}\/herd-app\/rest\//g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{RDS_HOST}}/${metastorDBHost}/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{METASTOR_SVC_ACCOUNT}}/${herdAdminUsername}/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{DM_DATA_BUCKET}}/${herdS3BucketName}/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{ENV_GROUP}}/${environment}/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{MS_HIVE_0_13_USER}}/MS_Hive_0_13/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{NO_OF_RETRIES}}/5/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{TOTAL_PARTITION_THRESHOLD}}/100/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{SINGLE_OBJECT_PARTITION_THRESHOLD}}/50/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{PARTITION_AGE_THRESHOLD_IN_HOURS}}/2/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{MAX_CLUSTER}}/10/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{RETRY_INTERVAL}}/120/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{AUTO_SCALE_INTERVAL_IN_MINUTES}}/2/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{MAX_CLUSTER_TO_START}}/2/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{CLUSTER_DEF_NAME}}/metastoreHiveClusterDefinition/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{EMAIL_LIST_FORMAT_CHANGE}}/mdl_team@finra.org/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{CREATE_CLUSTER_RETRY_COUNT}}/5/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{BACK_LOADING_CHUNK_SIZE}}/100/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{BACK_LOADING_PAGINATION_PAGE_SIZE}}/1000/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{EMR_IDLE_TIME_OUT}}/10/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{EMAIL_HOST}}/`hostname`/g\" ${config_file_location}"
execute_cmd "sed -i \"s/{{AGS}}/METASTOR/g\" ${config_file_location}"

# do not log credentials
sed -i "s/{{{MS_HIVE_0_13_PWD}}}/${hivePassword}/g" ${config_file_location}
check_error $? "sed {{HIVE_PASSWORD}} application.props"

execute_cmd "mkdir -p /home/hadoop/dmCreds/"
echo -n "${herdAdminUsername}:${herdAdminPassword}" | base64 > /home/hadoop/dmCreds/dmPass.base64

# Execute the metastor script
execute_cmd "cd ${deployLocation}/managedObjectLoader/scripts"
execute_cmd "chmod 755 runObjectProcessor.sh"
execute_cmd "./runObjectProcessor.sh ${deployLocation} ${deployLocation}/dmCreds/dmPass.base64"

echo "Everything looks good"

exit 0
