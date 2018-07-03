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
herdDatabaseNonRootUserPassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/HERD/RDS/${herdDatabaseNonRootUser}Account --with-decryption --region ${region} --output text --query Parameter.Value)

# Deploy tomcat files
execute_cmd "wget --quiet --random-wait http://central.maven.org/maven2/org/finra/herd/herd-war/${herdVersion}/herd-war-${herdVersion}.war -O /usr/share/tomcat8/webapps/herd-app.war"
execute_cmd "wget --quiet --random-wait http://central.maven.org/maven2/org/postgresql/postgresql/9.4-1201-jdbc41/postgresql-9.4-1201-jdbc41.jar --directory-prefix=/usr/share/tomcat8/lib/"
execute_cmd "wget --quiet --random-wait http://central.maven.org/maven2/mysql/mysql-connector-java/5.1.39/mysql-connector-java-5.1.39.jar --directory-prefix=/usr/share/tomcat8/lib/"

# Context.xml
execute_cmd "rm -f /usr/share/tomcat8/conf/context.xml"
execute_cmd "cp ${deployLocation}/conf/context.xml /usr/share/tomcat8/conf/context.xml"
execute_cmd "chmod 664 /usr/share/tomcat8/conf/context.xml"
execute_cmd "chown tomcat:tomcat /usr/share/tomcat8/conf/context.xml"

# web.xml
execute_cmd "cp ${deployLocation}/xml/install/web.xml /usr/share/tomcat8/conf/web.xml"
execute_cmd "sed -i \"s/REPLACE_Shepherd_DOMAIN_NAME/${mdlInstanceName}shepherd.${domainNameSuffix}/g\" /usr/share/tomcat8/conf/web.xml"
shepherdWebSiteBucketNameOnly=`echo ${shepherdWebSiteBucketUrl} | cut -d"/" -f3-`
execute_cmd "sed -i \"s/REPLACE_Shepherd_BUCKET_URL/${shepherdWebSiteBucketNameOnly}/g\" /usr/share/tomcat8/conf/web.xml"

# server.xml - Handle HTTP vs HTTPS
if [ "${httpProtocol}" = "https" ] ; then
    execute_cmd "sed -i \"s/{{HTTP_CONNECTOR}}//g\" ${deployLocation}/xml/install/server.xml"
else
    sed -i 's/{{HTTP_CONNECTOR}}/<Connector port="80" protocol="HTTP\/1.1" connectionTimeout="20000" redirectPort="8443" \/>/g' ${deployLocation}/xml/install/server.xml
    check_error $? "sed -i {{HTTP_CONNECTOR}} ${deployLocation}/xml/install/server.xml"
fi
execute_cmd "cp ${deployLocation}/xml/install/server.xml /usr/share/tomcat8/conf/server.xml"
execute_cmd "chmod 664 /usr/share/tomcat8/conf/server.xml"
execute_cmd "chown tomcat:tomcat /usr/share/tomcat8/conf/server.xml"
execute_cmd "sed -i \"s/REPLACE_DB_NAME/${herdDatabaseName}/g\" /usr/share/tomcat8/conf/server.xml"
execute_cmd "sed -i \"s/REPLACE_DB_HOST/${herdDatabaseHost}/g\" /usr/share/tomcat8/conf/server.xml"
execute_cmd "sed -i \"s/REPLACE_USER_NAME/${herdDatabaseNonRootUser}/g\" /usr/share/tomcat8/conf/server.xml"

# Making sure that we escape any \ in the password
sed -i "s/REPLACE_PASSWORD/${herdDatabaseNonRootUserPassword}/g" /usr/share/tomcat8/conf/server.xml
check_error $? "sed -i herdDatabaseNonRootPassword server.xml"

# Creating a self signed certificate for tomcat
if [ ! -f /usr/share/tomcat8/.keystore ]; then
    execute_cmd "/usr/lib/jvm/jre/bin/keytool -genkey -alias tomcat -keyalg RSA -keystore /usr/share/tomcat8/.keystore -storepass changeit -keypass changeit -dname \"${certificateInfo}\"";
fi

exit 0
