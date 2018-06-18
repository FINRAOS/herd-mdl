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
    if [ ${return_code} -ne 0 ]
    then
        echo "$(date "+%m/%d/%Y %H:%M:%S") *** ERROR *** ${cmd} has failed with error $return_code"
        /opt/aws/bin/cfn-signal -e 1 -r "MDLT deploy and execution failed " ${DeployHostWaitHandle}

        # Upload the log files
        aws s3 cp /var/log/mdl-setup.log s3://$MDLDeploymentBucket/test-results/${StackName}_${timestamp}/
        aws s3 cp /var/log/mdl-func-test.log s3://$MDLDeploymentBucket/test-results/${StackName}_${timestamp}/
        aws s3 cp --recursive /tmp/sam s3://$MDLDeploymentBucket/test-results/${StackName}_${timestamp}/
        aws s3 cp /var/log/mdl-shutdown-test.log s3://$MDLDeploymentBucket/test-results/${StackName}_${timestamp}/
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
deployPropertiesFile=$1

# Source the properties
. ${deployPropertiesFile}

#setup the workspace
execute_cmd "cd /home/ec2-user"
execute_cmd "rm -rf mdl"
execute_cmd "mkdir -p mdl/build"
execute_cmd "aws s3 cp --only-show-errors s3://$MDLDeploymentBucket/mdl-bucket-deploy/$MDLRelease/herd/mdl.zip ."
execute_cmd "unzip -q mdl.zip -d ./mdl/build/"
execute_cmd "rm -rf mdl.zip"
execute_cmd "aws s3 cp s3://$MDLBuildBucket/mdlt/build/$MDLTBranch/scripts/test.sh ./test_mdl.sh"
execute_cmd "chmod 755 ./test_mdl.sh"
execute_cmd "rm -rf lib"
execute_cmd "mkdir lib"
execute_cmd "aws s3 cp --only-show-errors --recursive s3://$MDLBuildBucket/mdlt/build/$MDLTBranch/lib/runtime/ ./lib"

#execute test steps, copy logs and test results
execute_cmd "./test_mdl.sh setup $deployPropertiesFile &> /var/log/mdl-setup.log"
execute_cmd "./test_mdl.sh execute $deployPropertiesFile &> /var/log/mdl-func-test.log"

# Upload the log files
execute_cmd "aws s3 cp /var/log/mdl-setup.log s3://$MDLDeploymentBucket/test-results/${StackName}_${timestamp}/"
execute_cmd "aws s3 cp /var/log/mdl-func-test.log s3://$MDLDeploymentBucket/test-results/${StackName}_${timestamp}/"
execute_cmd "aws s3 cp --recursive /tmp/sam s3://$MDLDeploymentBucket/test-results/${StackName}_${timestamp}/"

# execute_cmd "sleep 8h"

if [ "${RollbackOnFailure}" = "true" ] ; then
    # echo "Sleep for 60 minutes before cleaning up the stack"
    execute_cmd "sleep 60m"
    execute_cmd "./test_mdl.sh shutdown $deployPropertiesFile &> /var/log/mdl-shutdown-test.log"
    execute_cmd "aws s3 cp /var/log/mdl-shutdown-test.log s3://$MDLDeploymentBucket/test-results/${StackName}_${timestamp}/"
    execute_cmd "/opt/aws/bin/cfn-signal -e 0 -r \"MDLT deploy and execution succeeded \" ${DeployHostWaitHandle}"
    # Tests are done. Delete the deploy host
    execute_cmd "aws cloudformation delete-stack --stack-name ${StackName} --region ${RegionName}"
else
    execute_cmd "/opt/aws/bin/cfn-signal -e 0 -r \"MDLT deploy and execution succeeded \" ${DeployHostWaitHandle}"
fi


echo "Tests are done"
exit 0
