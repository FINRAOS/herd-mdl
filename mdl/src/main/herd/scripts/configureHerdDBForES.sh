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

export PGDATABASE=$1
export PGUSER=$2
export PGPASSWORD=$3

echo "PGUSER: ${PGUSER}"
echo "PASSWORD: ${PGPASSWORD:0:4}"

# Following configurations are only for DB updates
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.rest.client.hostname';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.rest.client.hostname','${elasticsearchHostname}', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.rest.client.scheme';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.rest.client.scheme','http', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.rest.client.port';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.rest.client.port','8888', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.search.guard.enabled';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.search.guard.enabled','false', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'search.index.update.jms.listener.enabled';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('search.index.update.jms.listener.enabled','true', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.bdef.index.name';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.bdef.index.name', 'bdef', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.bdef.document.type';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.bdef.document.type', 'doc', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.tag.index.name';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.tag.index.name', 'tag', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.phrase.query.boost';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.phrase.query.boost','1000', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.best.fields.query.boost';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.best.fields.query.boost','100', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.phrase.prefix.query.boost';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.phrase.prefix.query.boost','1', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.phrase.prefix.query.boost';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.phrase.prefix.query.boost','1', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.highlight.posttags';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.highlight.posttags','</hlt>', NULL);\""

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'elasticsearch.highlight.pretags';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('elasticsearch.highlight.pretags','<hlt class=\\\"highlight\\\">', NULL);\""

exit 0
