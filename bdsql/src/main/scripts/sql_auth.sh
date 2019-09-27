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

. /home/hadoop/conf/deploy.props

execute_cmd "cd /home/hadoop"
execute_cmd "aws configure set default.region ${region}"

for i in sync_app_objects sync_user_objects show_acls; do
    execute_cmd "python scripts/sql_auth.py \"$i\""
done

#restart presto-server to cleanup presto cache for ldap
execute_cmd	"sudo stop presto-server"
execute_cmd	"sudo start presto-server"

mdlInstanceRowCount=0
# Wait presto sever to be up
while [ ${mdlInstanceRowCount} -ne 1 ] ; do
    echo "Waiting Presto server to be up"
    echo "presto-cli --catalog hive --schema mdl --execute \"show tables\""
    echo "------------------------------------------------------"
    presto-cli --catalog hive --schema mdl --execute "show tables"
    echo "------------------------------------------------------"
    mdlInstanceRowCount=`presto-cli --catalog hive --schema mdl --execute "show tables" | wc -l`
    sleep 1m
done
