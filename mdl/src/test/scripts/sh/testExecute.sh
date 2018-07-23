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
deployPropertiesFile=$1
testPropsFile=$2

# Source the properties
. ${deployPropertiesFile}
. ${testPropsFile}

#Copy mdlt files(which stores mdl stack output parameters) to ec2 ?? which file
execute_cmd "cd /home/ec2-user"

# Execute the test cases
execute_cmd "wget http://central.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.0.0-M4/junit-platform-console-standalone-1.0.0-M4.jar -O mdlt/junit-runner.jar"
#TODO need to remove this once test version same as release version
releaseVersion="1.2.0"
if [ "${EnableSSLAndAuth}" = "true" ]
then
    excludeTestTag="noAuthTest"
else
    excludeTestTag="authTest"
fi
#don't check error code if rollbackOnFailure is true
if [ "${RollbackOnFailure}" = "true" ]
then
    java -DDeployPropertiesFile=${deployPropertiesFile} -jar mdlt/junit-runner.jar -p org.tsi.mdlt.test -T ${excludeTestTag} --details verbose --cp mdlt/herd-mdl-${releaseVersion}-tests.jar:mdlt/uber-herd-mdl-${releaseVersion}.jar --reports-dir /tmp/sam --disable-ansi-colors
else
    execute_cmd "java -DDeployPropertiesFile=${deployPropertiesFile} -jar mdlt/junit-runner.jar -p org.tsi.mdlt.test -T ${excludeTestTag} --details verbose --cp mdlt/herd-mdl-${releaseVersion}-tests.jar:mdlt/uber-herd-mdl-${releaseVersion}.jar --reports-dir /tmp/sam --disable-ansi-colors"
fi

exit 0
