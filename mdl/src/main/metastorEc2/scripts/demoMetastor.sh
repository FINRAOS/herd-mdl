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

function execute_curl_cmd {
	cmd="${1} --retry 5 --max-time 120 --retry-delay 7 --write-out \"\nHTTP_CODE:%{http_code}\n\" -u ${ldapMdlAppUsername}:${mdlUserLdapPassword}"
	echo "${1} --retry 5 --max-time 120 --retry-delay 7 --write-out \"\nHTTP_CODE:%{http_code}\n\" "
	eval $cmd > /tmp/curlCmdOutput 2>&1
	echo ""
	returnCode=`cat /tmp/curlCmdOutput | grep "HTTP_CODE" | cut -d":" -f2`
    if [ "${returnCode}" != "200" ]; then
        echo "$(date "+%m/%d/%Y %H:%M:%S") *** ERROR *** ${1} has failed with error ${returnCode}"
    	cat /tmp/curlCmdOutput
    	echo ""
        exit 1
    fi
}

function execute_uploader_cmd {
        cmd="java -jar ${deployLocation}/jenkins/herd-uploader-app.jar --force -e s3-external-1.amazonaws.com -l ${deployLocation}/data/${2}/ -m ${deployLocation}/data/${1} -V -H ${herdLoadBalancerDNSName} ${port} -R 3 -D 60"
        echo $cmd
        cmdWithCredentials="${cmd} -u ${ldapMdlAppUsername} -w ${mdlUserLdapPassword}"
        eval $cmdWithCredentials
        check_error ${PIPESTATUS[0]} "$cmd"
}

#MAIN
configFile="/home/mdladmin/deploy/mdl/conf/deploy.props"
if [ ! -f ${configFile} ] ; then
    echo "Config file does not exist ${configFile}"
    exit 1
fi
. ${configFile}

# Set the port and SSL information for uploader based on https or http
if [ "${httpProtocol}" = "https" ] ; then
    export port="-P 443 -s true"
else
    export port="-P 80"
fi

execute_cmd "echo \"From $0\""
mdlUserLdapPassword=$(aws ssm get-parameter --name ${ldapMdlAppUserPasswordParameterKey} --with-decryption --region ${region} --output text --query Parameter.Value)

# Registering metastor workflow
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/demo/securityDataObjectNotificationRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/notificationRegistrations/businessObjectDataNotificationRegistrations --insecure"
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/demo/tradeDataObjectNotificationRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/notificationRegistrations/businessObjectDataNotificationRegistrations --insecure"

# Uploading files Security Data files
execute_uploader_cmd "2017-08-01.security-data.manifest.json" "2017-08-01"
execute_uploader_cmd "2017-08-02.security-data.manifest.json" "2017-08-02"
execute_uploader_cmd "2017-08-03.security-data.manifest.json" "2017-08-03"
execute_uploader_cmd "2017-08-04.security-data.manifest.json" "2017-08-04"
execute_uploader_cmd "2017-08-07.security-data.manifest.json" "2017-08-07"
execute_uploader_cmd "2017-08-08.security-data.manifest.json" "2017-08-08"
execute_uploader_cmd "2017-08-09.security-data.manifest.json" "2017-08-09"
execute_uploader_cmd "2017-08-10.security-data.manifest.json" "2017-08-10"
execute_uploader_cmd "2017-08-11.security-data.manifest.json" "2017-08-11"

# Uploading files Trade Data files
execute_uploader_cmd "2017-08-01.trade-data.manifest.json" "2017-08-01"
execute_uploader_cmd "2017-08-02.trade-data.manifest.json" "2017-08-02"
execute_uploader_cmd "2017-08-03.trade-data.manifest.json" "2017-08-03"
execute_uploader_cmd "2017-08-04.trade-data.manifest.json" "2017-08-04"
execute_uploader_cmd "2017-08-07.trade-data.manifest.json" "2017-08-07"
execute_uploader_cmd "2017-08-08.trade-data.manifest.json" "2017-08-08"
execute_uploader_cmd "2017-08-09.trade-data.manifest.json" "2017-08-09"
execute_uploader_cmd "2017-08-10.trade-data.manifest.json" "2017-08-10"
execute_uploader_cmd "2017-08-11.trade-data.manifest.json" "2017-08-11"

echo "Everything looks good"

exit 0
