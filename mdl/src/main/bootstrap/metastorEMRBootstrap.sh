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

function updateHivePassword {
    # Get hive password
    hiveAccountPassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/HIVE/hiveAccount --with-decryption --region ${regionName} --output text --query Parameter.Value)
    echo "Waiting for hive-site.xml"
    while [[ `cat /etc/hive/conf/hive-site.xml 2>/dev/null | grep {{HIVE_PASSWORD}} | wc -l` = "0" ]]; do echo "."; sleep 1; done

    # Update the password
    sudo sed -i "s/{{HIVE_PASSWORD}}/${hiveAccountPassword}/g" /etc/hive/conf/hive-site.xml

    # Restart the services
    sudo stop hive-server2
    sudo stop hive-hcatalog-server
    sudo start hive-server2
    sudo start hive-hcatalog-server
}
#MAIN
mdlInstanceName=$1
environment=$2
regionName=$3

# Update client
execute_cmd "sudo yum -y install aws-cli"
execute_cmd "aws configure set default.region ${regionName}"

updateHivePassword &

echo "Everything is fine"

exit 0
