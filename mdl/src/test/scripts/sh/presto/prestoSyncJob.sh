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
configFile="/home/ec2-user/deployHost.props"
testConfigFile="/home/ec2-user/mdlt/conf/test.props"

if [ ! -f ${configFile} ] ; then
    echo "Config file does not exist ${configFile}"
    exit 1
fi
. ${configFile}
. ${testConfigFile}


echo "add presto sql_auth sync job"
PrestoClusterId=${BdsqlEMRPrestoCluster}
MDLStagingBucketName=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/S3/MDL --region ${RegionName} --output text --query Parameter.Value)
sqlAuthS3Location="s3://${MDLStagingBucketName}/BDSQL/sql_auth.sh"
stepId=$(aws emr add-steps --cluster-id ${PrestoClusterId} --steps Type=CUSTOM_JAR,Name=PrestoSyncJAR,ActionOnFailure=CONTINUE,Jar=s3://elasticmapreduce/libs/script-runner/script-runner.jar,Args=${sqlAuthS3Location} --query 'StepIds[0]' --output text)

echo "wait for the sync step to be done"
execute_cmd "aws emr wait step-complete --cluster-id ${PrestoClusterId} --step-id ${stepId}"
