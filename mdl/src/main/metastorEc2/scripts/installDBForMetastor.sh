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

# Execute the given command and support resume option
function execute_sql_cmd {
        cmd="${1}"
        sqlcmd="${2}"
        echo "${cmd} ${sqlcmd}"
        eval "${cmd} -p${metastorDBPassword} ${sqlcmd}"
        check_error ${PIPESTATUS[0]} "${cmd} ${sqlcmd}"
}

#MAIN
configFile="/home/mdladmin/deploy/mdl/conf/deploy.props"
if [ ! -f ${configFile} ] ; then
    echo "Config file does not exist ${configFile}"
    exit 1
fi
. ${configFile}

metastorDBPassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/RDS/hiveAccount --with-decryption --region ${region} --output text --query Parameter.Value)

# Change the RDS password if required
if [ "${metastorDBPassword}" = "changeit" ] ; then
    # changing DB password
    metastorDBPassword=$(openssl rand -base64 32 | tr -d /=+ | cut -c -16 )

    aws ssm put-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/RDS/hiveAccount --type "SecureString" --value ${metastorDBPassword} --region ${region} --overwrite
    check_error $? "aws ssm put-parameter --name '/app/MDL/${mdlInstanceName}/${environment}/METASTOR/RDS/hiveAccount' secure string"
fi

# Change the RDS password
aws rds modify-db-instance --db-instance-identifier ${metastoreDBInstance} --master-user-password ${metastorDBPassword} --cloudwatch-logs-export-configuration "{\"EnableLogTypes\":[\"error\",\"general\",\"audit\",\"slowquery\"]}" --apply-immediately --region ${region}
check_error $? "aws rds modify-db-instance --db-instance-identifier ${metastoreDBInstance}  modify password"
# Waiting for the new password to take effect
sleep 300

export hivePassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/HIVE/hiveAccount --with-decryption --region ${region} --output text --query Parameter.Value)
if [ "${hivePassword}" = "changeit" ] ; then
    hivePassword=$(openssl rand -base64 32 | tr -d /=+ | cut -c -16 )
    aws ssm put-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/HIVE/hiveAccount --type "SecureString" --value ${hivePassword} --region ${region} --overwrite
    check_error $? "aws ssm put-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/HIVE/hiveAccount secure string"
fi

export metastorDBPassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/RDS/hiveAccount --with-decryption --region ${region} --output text --query Parameter.Value)
export hivePassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/HIVE/hiveAccount --with-decryption --region ${region} --output text --query Parameter.Value)

# Create schema if required
if [ "${refreshDatabase}" = "true" ] ; then
    # Ignore error in case of first time setup
    mysql -h ${metastorDBHost} -u ${metastorDBUser} -p${metastorDBPassword} -D metastor -e "DROP DATABASE IF EXISTS metastor;"
    mysql -h ${metastorDBHost} -u ${metastorDBUser} -p${metastorDBPassword} -e "DROP SCHEMA IF EXISTS mshive23;"
    mysql -h ${metastorDBHost} -u ${metastorDBUser} -p${metastorDBPassword} -e "DROP SCHEMA IF EXISTS metastor;"
    mysql -h ${metastorDBHost} -u ${metastorDBUser} -p${metastorDBPassword} -e "DROP USER MS_Hive_0_13;"
    mysql -h ${metastorDBHost} -u ${metastorDBUser} -p${metastorDBPassword} -e "DROP USER MS_Presto;"

    # Get RDS Scripts
    execute_cmd "cd ${deployLocation}"
    execute_cmd "wget --quiet --random-wait https://github.com/FINRAOS/herd-mdl/releases/download/metastor-v${metastorVersion}/metastoreOperations-${metastorVersion}-dist.zip -O ${deployLocation}/metastoreOperations.zip"
    execute_cmd "wget --quiet --random-wait https://github.com/FINRAOS/herd-mdl/releases/download/metastor-v${metastorVersion}/managedObjectLoader-${metastorVersion}-dist.zip -O ${deployLocation}/managedObjectLoader.zip"

    execute_cmd "unzip metastoreOperations.zip"
    execute_cmd "unzip managedObjectLoader.zip"

    # Execute DB scripts for Metastor
    sed -i "s/{{HIVE_PASSWORD}}/${hivePassword}/g" ${deployLocation}/metastoreOperations/rds/metastorDefaultUsers.sql
    check_error $? "sed HIVE_PASSWORD ${deployLocation}/rds/metastorDefaultUsers.sql"
    mysql -h ${metastorDBHost} -u ${metastorDBUser} -p${metastorDBPassword} -e "CREATE DATABASE metastor;"
    check_error $? "create database metastor"
    execute_cmd "cd ${deployLocation}/metastoreOperations/rds"
    execute_sql_cmd "mysql -h ${metastorDBHost} -u ${metastorDBUser} -D metastor " "< ${deployLocation}/metastoreOperations/rds/metastorDefaultUsers.sql"
    execute_sql_cmd "mysql -h ${metastorDBHost} -u ${metastorDBUser} -D metastor " "< ${deployLocation}/metastoreOperations/rds/metastorSetupHive23.sql"
    execute_sql_cmd "mysql -h ${metastorDBHost} -u ${metastorDBUser} -D metastor " "< ${deployLocation}/metastoreOperations/rds/metastorSetup.sql"
fi

exit 0
