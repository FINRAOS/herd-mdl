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

timestamp=$(date +%Y-%m-%d_%H-%M-%S)

# Check the error and fail if the last command is NOT successful
function check_error {
    return_code=${1}
    cmd="$2"
    saveResult="$3"
    if [ ${return_code} -ne 0 ]
    then
        echo "$(date "+%m/%d/%Y %H:%M:%S") *** ERROR *** ${cmd} has failed with error $return_code"
        /opt/aws/bin/cfn-signal -e 1 -r "MDLT deploy and execution failed " ${DeployHostWaitHandle}
        #Save failed or passed mdlt result to s3
        if [ "${saveResult}" = "true" ]; then
            # Upload the all log files if mdlt failed in the middle
            aws s3 cp /var/log/mdl-setup.log s3://${MdltResultS3BucketName}/test-results/${MDLStackName}_${timestamp}/
            aws s3 cp /var/log/mdl-func-test.log s3://${MdltResultS3BucketName}/test-results/${MDLStackName}_${timestamp}/
            aws s3 cp --recursive /tmp/sam s3://${MdltResultS3BucketName}/test-results/${MDLStackName}_${timestamp}/
            aws s3 cp /var/log/mdl-shutdown-test.log s3://${MdltResultS3BucketName}/test-results/${MDLStackName}_${timestamp}/
        fi
        exit 1
    fi
}

# Execute the given command and support resume option
function execute_cmd {
        cmd="${1}"
        saveResult="${2}"
        echo $cmd
        eval $cmd
        check_error ${PIPESTATUS[0]} "$cmd" "$saveResult"
}

#MAIN
deployPropertiesFile=$1

# Source the properties
. ${deployPropertiesFile}

if [ "${MdltResultS3BucketName}" = '' ] ; then
    MdltResultS3BucketName=${MdltBucketName}
fi

#setup the workspace
execute_cmd "cd /home/ec2-user"

##1. Create public bucket
execute_cmd "aws s3api create-bucket --bucket ${MdltBucketName} --acl public-read-write --region ${RegionName}"

##2. Copy yaml file to s3 bucket
wrapperStackYml="installWrapperStack.yml"
execute_cmd "wget ${InstallMdlYmlUrl} -O ${wrapperStackYml}"
#no need to save to mdlt, can create stack using ec2 local file
execute_cmd "aws s3 cp ${wrapperStackYml} s3://${MdltBucketName}/cft/InstallMDL.yml"

##3. Copy mdlt yaml files to s3 bucket to be used in mdlt
execute_cmd "aws s3 cp --recursive mdlt/scripts/cft s3://${MdltBucketName}/cft"

testPropsFile="/home/ec2-user/mdlt/conf/test.props"
#execute test steps, copy logs and test results
execute_cmd "./mdlt/scripts/sh/testSetup.sh $deployPropertiesFile $testPropsFile &> /var/log/mdl-setup.log" "true"
execute_cmd "aws s3 cp /var/log/mdl-setup.log s3://${MdltResultS3BucketName}/test-results/${MDLStackName}_${timestamp}/"

execute_cmd "./mdlt/scripts/sh/testExecute.sh $deployPropertiesFile $testPropsFile &> /var/log/mdl-func-test.log" "true"
execute_cmd "aws s3 cp /var/log/mdl-func-test.log s3://${MdltResultS3BucketName}/test-results/${MDLStackName}_${timestamp}/"
execute_cmd "aws s3 cp --recursive /tmp/sam s3://${MdltResultS3BucketName}/test-results/${MDLStackName}_${timestamp}/"

#signal mdlt deploy host success
execute_cmd "/opt/aws/bin/cfn-signal -e 0 -r \"MDLT deploy and execution succeeded \" \"${DeployHostWaitHandle}\" "

#shutdown the deploy host after test execution
if [ "${RollbackOnFailure}" = "true" ] ; then
    # echo "Sleep for 60 minutes before cleaning up the stack"
    execute_cmd "sleep 60m"

    . ${testPropsFile}
    if [ "${existingStack}" = "false" ] ; then
        execute_cmd "./mdlt/scripts/sh/testShutdown.sh ${deployPropertiesFile} ${testPropsFile} &> /var/log/mdl-shutdown-test.log" "true"
        execute_cmd "aws s3 cp /var/log/mdl-shutdown-test.log s3://${MdltResultS3BucketName}/test-results/${MDLStackName}_${timestamp}/"
    fi
    # Tests are done. Delete the deploy host
    execute_cmd "aws cloudformation delete-stack --stack-name ${MDLTStackName} --region ${RegionName}"
fi


echo "Tests are done"
exit 0
