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
mdlUserLdapPassword=$(aws ssm get-parameter --name ${ldapMdlAppUserPasswordParameterKey} --with-decryption --region ${region} --output text --query Parameter.Value)
ldapMdlAppUsername=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/MdlAppUsername --with-decryption --region ${region} --output text --query Parameter.Value)

# Change the application.properties for metastor
execute_cmd "sed -i \"s/{{METASTOR_SVC_ACCOUNT}}/${ldapMdlAppUsername}/g\" ${deployLocation}/conf/application.properties"
execute_cmd "sed -i \"s/{{DM_HOST}}/${httpProtocol}:\/\/${herdLoadBalancerDNSName}\/herd-app\/rest\//g\" ${deployLocation}/conf/application.properties"
execute_cmd "sed -i \"s/{{RDS_HOST}}/${metastorDBHost}/g\" ${deployLocation}/conf/application.properties"
execute_cmd "sed -i \"s/{{DM_DATA_BUCKET}}/${herdS3BucketName}/g\" ${deployLocation}/conf/application.properties"
execute_cmd "sed -i \"s/{{ENVIRONMENT}}/${environment}/g\" ${deployLocation}/conf/application.properties"
execute_cmd "sed -i \"s/{{CRED_STASH_SVC_ACC_ID}}/${mdlInstanceName}/g\" ${deployLocation}/conf/application.properties"
sed -i "s/{{METASTOR_SVC_ACCOUNT_PWD}}/${mdlUserLdapPassword}/g" ${deployLocation}/conf/application.properties
check_error $? "sed {{METASTOR_SVC_ACCOUNT_PWD}} application.props"
sed -i "s/{{HIVE_PASSWORD}}/${hivePassword}/g" ${deployLocation}/conf/application.properties
check_error $? "sed {{HIVE_PASSWORD}} application.props"

execute_cmd "mkdir -p /home/hadoop/dmCreds/"
execute_cmd "echo ${ldapMdlAppUsername}:${mdlUserLdapPassword} | base64 > /home/hadoop/dmCreds/dmPass.base64"
execute_cmd "cp ${deployLocation}/conf/application.properties ${deployLocation}/jenkins/deploy/managedObjectLoader/scripts/config/application.properties"

# Execute the metastor script
execute_cmd "cd /home/hadoop/jenkins/deploy/managedObjectLoader/scripts"
execute_cmd "chmod 755 runObjectProcessor.sh"
execute_cmd "./runObjectProcessor.sh"

echo "Everything looks good"

exit 0
