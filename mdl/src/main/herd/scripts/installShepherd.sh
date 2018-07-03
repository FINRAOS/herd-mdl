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

execute_cmd "echo \"From $0\""
# Download and serve herd-ui (shepherd)
execute_cmd "mkdir -p ${deployLocation}/herd-ui"
execute_cmd "wget --quiet --random-wait https://registry.npmjs.org/@herd/herd-ui-dist/-/herd-ui-dist-${herdUiVersion}.tgz -O ${deployLocation}/herd-ui/herd-ui.tgz"
execute_cmd "cd ${deployLocation}/herd-ui"
execute_cmd "tar -xzf herd-ui.tgz"
execute_cmd "cd package/dist"
execute_cmd "aws s3 sync . s3://${shepherdS3BucketName}"

# Change Shepherd based on authentication selection
if [ "${enableSSLAndAuth}" = "true" ] ; then
    execute_cmd "sed -i \"s/{{USE_BASIC_AUTH}}/true/g\" ${deployLocation}/conf/configuration.json"
    execute_cmd "sed -i \"s/{{HERD_URL}}/${httpProtocol}:\/\/${mdlInstanceName}herd.${domainNameSuffix}/g\" ${deployLocation}/conf/configuration.json"
    execute_cmd "sed -i \"s/{{BASIC_AUTH_REST_UI}}/${httpProtocol}:\/\/${mdlInstanceName}herd.${domainNameSuffix}/g\" ${deployLocation}/conf/configuration.json"
else
    execute_cmd "sed -i \"s/{{USE_BASIC_AUTH}}/false/g\" ${deployLocation}/conf/configuration.json"
    execute_cmd "sed -i \"s/{{BASIC_AUTH_REST_UI}}//g\" ${deployLocation}/conf/configuration.json"
    execute_cmd "sed -i \"s/{{HERD_URL}}/${httpProtocol}:\/\/${herdLoadBalancerDNSName}/g\" ${deployLocation}/conf/configuration.json"
fi
execute_cmd "aws s3 cp ${deployLocation}/conf/configuration.json s3://${shepherdS3BucketName}"

exit 0
