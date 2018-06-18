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

function log(){

    # Log output to screen
    # input: log message

    local MSG="$1"

    local LOG_SOURCE=$(basename $0 2> /dev/null || echo "logger")
    echo "$(date '+%F %T') bdsql-$LOG_SOURCE[$$]: $MSG"
}

function run_preto_query(){

    local APP_USER="$1"
    local QUERY="$2"

    if [ "${enableSSLAndAuth}" = "true" ] ; then
    log "executing query with SSL : user=$APP_USER query=$QUERY"
        presto-cli \
            --server https://$HOSTNAME.ec2.internal:5439 \
            --keystore-path /etc/presto/mdl.jks \
            --keystore-password changeit \
            --catalog hive \
            --schema "user_$APP_USER" \
            --user "$APP_USER" \
            --password \
            --execute "$QUERY"
    else
        log "executing query: user=$APP_USER query=$QUERY"
        presto-cli \
            --server http://$HOSTNAME.ec2.internal:8889 \
            --catalog hive \
            --schema "user_$APP_USER" \
            --execute "$QUERY"
    fi

}


function cleanup_tmp_objects(){

    log "removing tmp objects"
    run_preto_query "$APP_USER" "SHOW TABLES" | sed -e 's/"//g' | egrep '^t[0-9]+(_|$)' | while read t; do
        run_preto_query "$APP_USER" "DROP TABLE user_$APP_USER.$t"
    done

}

function main(){

    # test HTTP port/reponse
    #presto-cli --server http://$HOSTNAME.ec2.internal:8889 --catalog hive --schema sec_market_data --execute "select * from securitydata_mdl_txt limit 10"
    #presto-cli --server http://$HOSTNAME.ec2.internal:8889 --catalog hive --schema mdl --execute "select * from mdl_object_mdl_txt limit 10"

    #presto-cli --server http://$HOSTNAME.ec2.internal:8889 --user "$APP_USER" --catalog hive --schema sec_market_data --execute "select * from securitydata_mdl_txt limit 10"
    #presto-cli --server http://$HOSTNAME.ec2.internal:8889 --user "$APP_USER" --catalog hive --schema mdl --execute "select * from mdl_object_mdl_txt limit 10"

    log "started running smoke tests"

    cleanup_tmp_objects
    echo

    run_preto_query "$APP_USER" "select * from system.runtime.nodes"
    run_preto_query "$APP_USER" "select * from system.metadata.catalogs"
    echo

    run_preto_query "$APP_USER" "select current_timestamp"
    echo

    run_preto_query "$APP_USER" "show schemas"
    run_preto_query "$APP_USER" "show tables"
    echo

    run_preto_query "$APP_USER" "create table t0_default (my_id bigint, my_string varchar)"
    run_preto_query "$APP_USER" "insert into t0_default values (1,'abc')"
    run_preto_query "$APP_USER" "select * from t0_default"
    echo

    run_preto_query "$APP_USER" "create table t0_orc (my_id bigint, my_string varchar) with (format = 'ORC')"
    run_preto_query "$APP_USER" "create table t0_text (my_id bigint, my_string varchar) with (format = 'TEXTFILE')"
    echo

    run_preto_query "$APP_USER" "create table t1 as select * from mdl.mdl_object_mdl_txt limit 10"
    run_preto_query "$APP_USER" "select * from t1"
    echo

    cleanup_tmp_objects
    echo

    log "finished running smoke tests"

}

#MAIN

. /home/hadoop/conf/deploy.props

export APP_USER=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/MdlAppUsername --with-decryption --output text --query Parameter.Value)
export APP_PASS=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/MDLAppPassword --with-decryption --output text --query Parameter.Value)
export PRESTO_PASSWORD="$APP_PASS"

main 2>&1
