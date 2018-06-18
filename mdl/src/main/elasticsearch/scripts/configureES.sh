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

# Create elasticsearch data and log directories
execute_cmd "mkdir -p /herd-es/data"
execute_cmd "mkdir -p /herd-es/log"
execute_cmd "chown -R elasticsearch:elasticsearch /herd-es"

# Configure elasticsearch
TOTAL_MEMORY=$(awk '/MemTotal/ {print int($2*0.49/1024)}' /proc/meminfo)
MAX_MEMORY=$((32*1024))

if [ ${TOTAL_MEMORY} -gt ${MAX_MEMORY} ]; then
   TOTAL_MEMORY=$MAX_MEMORY
fi

echo Memory for elasticsearch : ${TOTAL_MEMORY}m
execute_cmd "sed -i \"s/-Xmx[0-9]g/-Xmx${TOTAL_MEMORY}m/g\" /etc/elasticsearch/jvm.options"
execute_cmd "sed -i \"s/-Xms[0-9]g/-Xms${TOTAL_MEMORY}m/g\" /etc/elasticsearch/jvm.options"

execute_cmd "echo -e 'network.host: 0.0.0.0' >> /etc/elasticsearch/elasticsearch.yml"
execute_cmd "echo -e 'cluster.name: elasticsearch' >> /etc/elasticsearch/elasticsearch.yml"
execute_cmd "echo -e 'path.data: /herd-es/data' >> /etc/elasticsearch/elasticsearch.yml"
execute_cmd "echo -e 'path.logs: /herd-es/log' >> /etc/elasticsearch/elasticsearch.yml"
execute_cmd "echo -e 'http.enabled: true' >> /etc/elasticsearch/elasticsearch.yml"
execute_cmd "echo -e 'script.engine.groovy.inline.search: on' >> /etc/elasticsearch/elasticsearch.yml"
execute_cmd "echo -e 'elasticsearch soft memlock unlimited' >> /etc/security/limits.conf"
execute_cmd "echo -e 'elasticsearch hard memlock unlimited' >> /etc/security/limits.conf"
execute_cmd "sed -i \"/ swap / s/^/#/\" /etc/fstab"

exit 0