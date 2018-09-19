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
        echo="${2}"
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

# This is to enable SSL communication between tomcat and postgres
tmp=`/usr/bin/sudo /usr/bin/keytool -keystore /usr/lib/jvm/jre/lib/security/cacerts -storepass changeit -list | /bin/grep rds_postgres`
if [ "${tmp}" = "" ]; then
    execute_cmd "/usr/bin/curl https://s3.amazonaws.com/rds-downloads/rds-combined-ca-bundle.pem > /tmp/rds-combined-ca-bundle.pem";
    execute_cmd "/usr/bin/openssl x509 -outform der -in /tmp/rds-combined-ca-bundle.pem -out /tmp/rds-combined-ca-bundle.der";
    execute_cmd "/usr/bin/yes | /usr/bin/sudo /usr/bin/keytool -keystore /usr/lib/jvm/jre/lib/security/cacerts -alias rds_postgres -import -file /tmp/rds-combined-ca-bundle.der -storepass changeit";
    execute_cmd "rm /tmp/rds-combined-ca-bundle.pem"
    execute_cmd "rm /tmp/rds-combined-ca-bundle.der"
fi

export PGDATABASE=$herdDatabaseName

# Execute DB scripts
herdDatabasePassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/HERD/RDS/masterAccount --with-decryption --region ${region} --output text --query Parameter.Value)
if [ "${herdDatabasePassword}" = "" ] ; then
    # changing DB password
    herdDatabasePassword=$(openssl rand -base64 32 | tr -d /=+ | cut -c -16)
    aws ssm put-parameter --name /app/MDL/${mdlInstanceName}/${environment}/HERD/RDS/masterAccount --type SecureString --value ${herdDatabasePassword} --region ${region}
    check_error $? "aws ssm put-parameter --name '/app/MDL/${mdlInstanceName}/${environment}/HERD/RDS/masterAccount' secure string"
fi

# Change RDS password to the value in parameter store
aws rds modify-db-instance --db-instance-identifier ${herdDBInstance} --master-user-password ${herdDatabasePassword} --apply-immediately --region ${region}
check_error $? "aws rds modify-db-instance --db-instance-identifier ${herdDBInstance} modify password"
#sleep 2 minutes to wait for the rds status changed to reseting-password
sleep 180
# Waiting for the new password to take effect, which is waiting until rds is available again
execute_cmd "aws rds wait db-instance-available --db-instance-identifier ${herdDBInstance} --region ${region}"

# Schema password
herdDatabaseNonRootUserPassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/HERD/RDS/${herdDatabaseNonRootUser}Account --with-decryption --region ${region} --output text --query Parameter.Value)

if [ "${herdDatabaseNonRootUserPassword}" = "" ] ; then
    # Create a DB non root user password for herd
    herdDatabaseNonRootUserPassword=$(openssl rand -base64 32 | tr -d /=+ | cut -c -16 )

    aws ssm put-parameter --name /app/MDL/${mdlInstanceName}/${environment}/HERD/RDS/${herdDatabaseNonRootUser}Account --type "SecureString" --value ${herdDatabaseNonRootUserPassword} --region ${region}
    check_error $? "aws ssm put-parameter --name /app/MDL/${mdlInstanceName}/${environment}/HERD/RDS/${herdDatabaseNonRootUser}Account secure string"
fi

# Change Schema password to the value in parameter store
export PGPASSWORD=$herdDatabasePassword
export PGUSER=$herdDatabaseUser
psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c "ALTER user ${herdDatabaseNonRootUser} WITH PASSWORD '${herdDatabaseNonRootUserPassword}';"

if [[ "${refreshDatabase}" = "true" ]] ; then
    # Ignore error in case of first time setup
    export PGUSER=$herdDatabaseNonRootUser
    export PGPASSWORD=$herdDatabaseNonRootUserPassword
    psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c "DROP SCHEMA IF EXISTS ${herdDatabaseNonRootUser} CASCADE;"
    export PGPASSWORD=$herdDatabasePassword
    export PGUSER=$herdDatabaseUser
    psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c "DROP ROLE IF EXISTS ${herdDatabaseNonRootUser};"
else
    echo "Database is not refreshed"
    exit 0
fi

# Create a DB user, role and schema for herd
export PGPASSWORD=$herdDatabasePassword
export PGUSER=$herdDatabaseUser
execute_cmd "sed -i \"s/{{HERD_DB_USER}}/${herdDatabaseNonRootUser}/g\" ${deployLocation}/sql/herdSchema.sql"
sed -i "s/{{HERD_DB_USER_PASSWORD}}/${herdDatabaseNonRootUserPassword}/g" ${deployLocation}/sql/herdSchema.sql
check_error $? "sed  HERD_DB_USER_PASSWORD ${deployLocation}/sql/herdSchema.sql"
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/herdSchema.sql"

# Run all the initialization scripts as the new user
export PGUSER=$herdDatabaseNonRootUser
export PGPASSWORD=$herdDatabaseNonRootUserPassword

# Fetch the Herd DB scripts package and inflate to staging location
execute_cmd "wget --quiet --random-wait http://central.maven.org/maven2/org/finra/herd/herd-scripts-sql/${herdVersion}/herd-scripts-sql-${herdVersion}.jar --directory-prefix=${deployLocation}/sql"
execute_cmd "unzip ${deployLocation}/sql/herd-scripts-sql-${herdVersion}.jar -d ${deployLocation}/sql/"

# Execute all the DB scripts to create from scratch
# 1) Create herd tables
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/herd.postgres.0.1.0.create.sql"

# 2) Create Quartz tables. Note that this file is distributed by Quartz (v2.2.1) and is included out of convenience
# We do not stop on error for this script, because it will always throw an exception because it tries to drop the
# tables before creating them
execute_cmd "psql --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/quartz_tables_postgres.sql"

# 3) Create Activiti tables. Note that these files are distributed by Activiti (v5.16.3.0), and are included out of convenience,
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/activiti.postgres.create.engine.sql"
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/activiti.postgres.create.history.sql"
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/activiti.postgres.create.identity.sql"

# 4) Insert reference data
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/herd.postgres.0.1.0.refdata.sql"

# 5) Configure environment
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/herd.postgres.0.1.0.cnfgn.sql"

pre=""
post=""

initial=1
version=$(echo ${herdVersion} | cut -d. -f2)

while [ ${initial} -lt ${version} ]
do
    if [ ${initial} -lt 10 ]; then
        next=$((10#${initial}+1))
        pre="0${initial}"
        if [ ${initial} -eq 9 ]; then
            post="${next}"
        else
            post="0${next}"
        fi
    else
        pre="${initial}"
        next=$((10#${initial}+1))
        post="${next}"
    fi
    ((initial++))

# Apply incremental upgrade scripts to the Herd DB
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/herd.postgres.0.${pre}.0-to-0.${post}.0.upgrade.sql"

done

execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"alter table ec2_od_prcng_lk alter column rgn_nm type character varying(250);\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"alter table ec2_od_prcng_lk alter column instc_type type character varying(250);\""

# Enable LDAP if specified
if [ "${enableSSLAndAuth}" = "true" ] ; then
    # Enable Herd namespace authorization
    execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/herdAuthConfiguration.sql"

    # Ignore error for first time
    psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c "DELETE FROM cnfgn WHERE cnfgn_key_nm = 'security.enabled.spel.expression';"

    # Get Auth group-names from parameter store
    admin_group=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/AuthGroup/Admin --with-decryption --region ${region} --output text --query Parameter.Value)
    read_only_group=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/AuthGroup/RO --with-decryption --region ${region} --output text --query Parameter.Value)

    mdl_read_write_group=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/AuthGroup/MDL --with-decryption --region ${region} --output text --query Parameter.Value)
    sec_read_write_group=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/AuthGroup/SEC --with-decryption --region ${region} --output text --query Parameter.Value)

    # Add security role functions for auth groups
    execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/herdSecurityRoleFunctionsAdmin.sql -v scrty_role=\"${admin_group}\""
    execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/herdSecurityRoleFunctionsReadOnly.sql -v scrty_role=\"${read_only_group}\""
    execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/herdSecurityRoleFunctionsReadWrite.sql -v scrty_role=\"${mdl_read_write_group}\""
    execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/herdSecurityRoleFunctionsReadWrite.sql -v scrty_role=\"${sec_read_write_group}\""

    # Get admin user
    admin_user=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/User/HerdAdminUsername --with-decryption --region ${region} --output text --query Parameter.Value)
    admin_user_url="${admin_user}"

    # Add namespace authorization admin permissions for the app-admin user
    execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO dmrowner.user_tbl VALUES ('${admin_user_url}', 'USER', 'ADMIN', current_timestamp, current_timestamp, 'SYSTEM', 'SYSTEM', '${PGUSER}', 'Y', 'Y');\""
fi

# Configuration for Elasticsearch
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/elasticsearch.configuration.values.sql"
execute_cmd "${deployLocation}/scripts/configureHerdDBForES.sh $PGDATABASE $PGUSER ${herdDatabaseNonRootUserPassword}"

# Herd configurations
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -f ${deployLocation}/sql/herdConfiguration.sql"

# Following configurations are only for DB updates
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 's3.managed.bucket.name';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('s3.managed.bucket.name','${herdS3BucketName}', NULL);\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'herd.notification.sqs.incoming.queue.name';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('herd.notification.sqs.incoming.queue.name','${herdQueueInName}', NULL);\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"DELETE FROM cnfgn WHERE cnfgn_key_nm = 'search.index.update.sqs.queue.name';\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO cnfgn VALUES ('search.index.update.sqs.queue.name','${searchIndexUpdateSqsQueueName}', NULL);\""

log4j_config_value=$(<${deployLocation}/xml/install/log4j-override-config.xml)
##insert rows to ec2_price_table;
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO dmrowner.ec2_od_prcng_lk VALUES (5878, 'us-east-1', 'm4.large', 0.10000, now(), 'SYSTEM', now(), 'SYSTEM');\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO dmrowner.ec2_od_prcng_lk VALUES (5879, 'us-east-1', 'm4.xlarge', 0.20000, now(), 'SYSTEM', now(), 'SYSTEM');\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO dmrowner.ec2_od_prcng_lk VALUES (5282, 'us-east-1', 'm4.4xlarge', 0.80000, now(), 'SYSTEM', now(), 'SYSTEM');\""
execute_cmd "psql --set ON_ERROR_STOP=on --host ${herdDatabaseHost} --port 5432 -c \"INSERT INTO dmrowner.ec2_od_prcng_lk VALUES (5895, 'us-east-1', 'm4.2xlarge', 0.40000, now(), 'SYSTEM', now(), 'SYSTEM');\""

exit 0
