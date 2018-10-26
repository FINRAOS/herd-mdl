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

execute_cmd "aws configure set default.region ${region}"

# pull the official amazon linux image
execute_cmd "docker pull amazonlinux"

# start a container and copy the script which prepares the deployment package
execute_cmd "docker run -v ${deployLocation}/scripts/buildNsAuthDeploymentPackage.sh:/build_script.sh --name lambda_build amazonlinux bash build_script.sh"

# copy the deployment bundle from docker container to local filesystem
execute_cmd "docker cp lambda_build:/build/ns_auth_sync_utility.zip ."

# add the actual lambda script to the deployment bundle
execute_cmd "cp ${deployLocation}/scripts/ns_auth_sync_utility.py ."
execute_cmd "zip -ur ns_auth_sync_utility.zip ns_auth_sync_utility.py"

# set read permissions on the file
execute_cmd "chmod 444 ns_auth_sync_utility.zip"

# upload lambda deployment package to S3
execute_cmd "aws s3 cp ns_auth_sync_utility.zip s3://${deploymentBucketName}/${releaseVersion}/lambda/"
