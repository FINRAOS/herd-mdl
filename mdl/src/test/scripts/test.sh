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
step=$1
deployPropertiesFile=$2

# Source the properties
. ${deployPropertiesFile}

if [ "${step}" != "setup" ] && [ "${step}" != "execute" ] && [ "${step}" != "shutdown" ] ; then
    echo "Invalid arguments"
    echo "$0 <setup|execute|shutdown> <deployPropertiesFile>"
    exit 1
fi

execute_cmd "cd /home/ec2-user"

if [ "${step}" != "execute" ] ; then
	execute_cmd "rm -rf mdlt"
	execute_cmd "mkdir mdlt"
	execute_cmd "cd mdlt"
	execute_cmd "aws s3 cp --recursive --exclude \"*test.sh\" --exclude \"*testRunner.sh\" --exclude \"*/runtime/*.jar\" s3://$MDLBuildBucket/mdlt/build/$MDLTBranch/ ."
	execute_cmd "cd .."
else
	execute_cmd "find mdlt ! -name 'conf' ! -name 'mdlt' -type d -exec rm -rf {} +"
	execute_cmd "cd mdlt"
	execute_cmd "aws s3 cp --recursive --exclude \"*test.sh\" --exclude \"*testRunner.sh\" --exclude \"*/runtime/*.jar\" --exclude \"*test.props\" s3://$MDLBuildBucket/mdlt/build/$MDLTBranch/ ."
	execute_cmd "cd .."
fi
execute_cmd "chmod 755 -R ./mdlt/scripts/sh"

if [ "${step}" = "setup" ] ; then
	execute_cmd "./mdlt/scripts/sh/testSetup.sh $deployPropertiesFile"
elif [ "${step}" = "shutdown" ] ; then
	execute_cmd "./mdlt/scripts/sh/testShutdown.sh $deployPropertiesFile"
else
	execute_cmd "./mdlt/scripts/sh/testExecute.sh $deployPropertiesFile"
fi		

exit 0
