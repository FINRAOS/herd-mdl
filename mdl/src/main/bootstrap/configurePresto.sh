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
        retry="${2}"
        echo $cmd
        eval $cmd
        returnCode=${PIPESTATUS[0]}
        if [ ${returnCode} -ne 0 ] ; then
            if [ "${retry}" = "RETRY" ] ; then
                sleep 2m
                eval $cmd
                check_error ${PIPESTATUS[0]} "$cmd"
            else
                check_error ${returnCode} "$cmd"
            fi
        fi
}

#MAIN
deployBucket=$1
deployBucketKey=$2
region=$3
waitHandle=$4
deployProps=$5
bdsqlVersion=$6

execute_cmd "aws configure set default.region ${region}"

# Download deploy properties
execute_cmd "mkdir -p /home/hadoop/conf"
execute_cmd "aws s3 cp ${deployProps} /home/hadoop/conf/"
. /home/hadoop/conf/deploy.props

# Deploy the package
execute_cmd "cd /home/hadoop"
execute_cmd "wget --quiet --random-wait https://s3.amazonaws.com/mdl-artifacts-storage/sidldap/bdsqlartifact/bdsql-1.0.0-dist.zip -O /home/hadoop/bdsql.zip"
execute_cmd "unzip bdsql.zip"
execute_cmd "chmod 755 scripts/*"

execute_cmd "scripts/configureBDSQL.sh"
execute_cmd "scripts/smokeTesting.sh"

# Signal to Cloud Stack
execute_cmd "/opt/aws/bin/cfn-signal -e 0 -r 'Bdsql Creation Complete' \"${waitHandle}\""


exit 0
