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

. /home/hadoop/conf/deploy.props

execute_cmd "cd /home/hadoop"

# Register the target instance to the ELB
targetGroupArn=`aws elbv2 describe-target-groups --region ${region} --names ${mdlInstanceName}-BdsqlTargetGroup | grep TargetGroupArn | cut -d":" -f2- | tr -d '\"' | tr -d ',' | tr -d ' '`
instanceId=`curl http://169.254.169.254/latest/meta-data/instance-id`
execute_cmd "aws elbv2 register-targets --target-group-arn ${targetGroupArn} --targets Id=${instanceId} --region ${region}"

echo "" > /tmp/config.properties

# Handle SSL
if [ "${enableSSLAndAuth}" = "true" ] ; then
    echo "http-server.https.enabled=true" >> /tmp/config.properties
    echo "http-server.https.port=5439" >> /tmp/config.properties
    echo "http-server.https.keystore.path=/etc/presto/mdl.jks" >> /tmp/config.properties
    echo "http-server.https.keystore.key=changeit" >> /tmp/config.properties
fi

# Handle authentication
if [ "${enableSSLAndAuth}" = "true" ] ; then
    # Get LDAP values
    ldapServer=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/HostName --output text --query Parameter.Value)
    ldapBaseDN=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/BaseDN --output text --query Parameter.Value)
    ldapAuthGroup=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/AuthGroup --output text --query Parameter.Value)

    # Configure Presto
    echo "http-server.authentication.type=LDAP" >> /tmp/config.properties
    echo "authentication.ldap.url=ldaps://${ldapServer}:636" >> /tmp/config.properties
    echo "authentication.ldap.user-bind-pattern=uid=\${USER},${ldapAuthGroup},${ldapBaseDN}" >> /tmp/config.properties
fi

# Restart presto
sudo sh -c "cat /tmp/config.properties >> /etc/presto/conf/config.properties"
execute_cmd	"sudo stop presto-server"
execute_cmd	"sudo start presto-server"

mdlInstanceRowCount=0
# Wait infinitely till the smoke testing data shows up. If the data does not show up for any reason, CFT will timeout
while [ ${mdlInstanceRowCount} -ne 3 ] ; do
    echo "Waiting for the smokeTesting data to show-up in Presto"
    echo "presto-cli --catalog hive --schema mdl --execute \"select * from mdl.mdl_object_mdl_txt\""
    echo "------------------------------------------------------"
    presto-cli --catalog hive --schema mdl --execute "select * from mdl.mdl_object_mdl_txt"
    echo "------------------------------------------------------"
    mdlInstanceRowCount=`presto-cli --catalog hive --schema mdl --execute "select * from mdl.mdl_object_mdl_txt" | grep ${mdlInstanceName} | wc -l`
    sleep 1m
done

# Handle authorization
if [ "${enableSSLAndAuth}" = "true" ] ; then
    # Change the role_map
    export metastorDBPassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/RDS/hiveAccount --with-decryption --region ${region} --output text --query Parameter.Value)
    mysql -h ${metastorDBHost} -u ${metastorDBUser} -p${metastorDBPassword} -D metastor -e " INSERT INTO ROLE_MAP VALUES (101,UNIX_TIMESTAMP(),1,'hive','USER','mdl_app','USER',1); ;"

    # Execute authorization codebase
    execute_cmd "sudo scripts/sql_auth.sh"
    execute_cmd "sudo aws s3 cp scripts/sql_auth.sh s3://${mdlStagingBucketName}/BDSQL/sql_auth.sh"
fi

echo "Everything looks good"

exit 0
