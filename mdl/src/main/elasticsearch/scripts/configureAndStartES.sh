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


execute_cmd "cd /home/mdladmin"
execute_cmd "aws configure set default.region ${region}"

# Set cloudwatch log group retention period
execute_cmd "aws logs put-retention-policy --log-group-name ${logGroupName} --retention-in-days ${cloudWatchRetentionDays}"

# Copy stack tags to cloudwatch log group
stack_tags=$(aws cloudformation describe-stacks --stack-name ${stackName} --query "Stacks[*].Tags" --output json | jq -c '.[]')
isTagExisted=$( echo jq -r '.[]' | jq 'any' <<<"${stack_tags}" )
if [ "${isTagExisted}" != "false" ] ; then
    echo "tagging sqs"
    cloudwatch_tags=$( echo jq -r '.[]' | jq 'from_entries' <<<"${stack_tags}" )
    cloudwatch_tags=${cloudwatch_tags//\"/\\\"}
    execute_cmd "aws logs tag-log-group --log-group-name ${logGroupName} --tags \"${cloudwatch_tags}\""
fi

#Configure cloudwatch log for elastic search
execute_cmd "sed -i \"s/{log_group_name}/${logGroupName}/g\" ${deployLocation}/conf/logs.conf"
execute_cmd "sudo bash -c 'echo >> /var/awslogs/etc/config/codedeploy_logs.conf; cat ${deployLocation}/conf/logs.conf >> /var/awslogs/etc/config/codedeploy_logs.conf'"
execute_cmd "sudo service awslogs restart"

# Configure apache
execute_cmd "sudo ${deployLocation}/scripts/configureApacheES.sh"

# Start apache
execute_cmd "echo \"From $0\""
execute_cmd "sudo service httpd start"
execute_cmd "sleep 15"

# Configure elasticsearch
execute_cmd "sudo ${deployLocation}/scripts/configureES.sh"

# Start elasticsearch
execute_cmd "echo \"From $0\""
execute_cmd "sudo service elasticsearch start"
execute_cmd "sleep 15"

# Health check command
execute_cmd "/usr/bin/curl http://localhost:8888/_cluster/health"

# Signal to Cloud Stack
execute_cmd "/opt/aws/bin/cfn-signal -e 0 -r 'Elasticsearch Creation Complete' \"${waitHandleForEs}\""

echo "Everything looks good"

exit 0
