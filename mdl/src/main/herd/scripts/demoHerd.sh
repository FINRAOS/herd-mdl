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

#MAIN
configFile="/home/mdladmin/deploy/mdl/conf/deploy.props"
if [ ! -f ${configFile} ] ; then
    echo "Config file does not exist ${configFile}"
    exit 1
fi
. ${configFile}

execute_cmd "echo \"From $0\""
mdlUserLdapPassword=$(aws ssm get-parameter --name ${ldapMdlAppUserPasswordParameterKey} --with-decryption --region ${region} --output text --query Parameter.Value)

if [ "${refreshDatabase}" = "true" ] ; then

    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/demo/marketDataNamespaceRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/namespaces --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/demo/dataProvider.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/dataProviders --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/demo/partitionKeyGroup.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/partitionKeyGroups --insecure"

    # Money Market Data - Registering objects
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/demo/moneyMarketDataObjectRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/businessObjectDefinitions --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/demo/moneyMarketDataFormatRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/businessObjectFormats --insecure"

    # Security Data - Registering objects
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/demo/securityDataObjectRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/businessObjectDefinitions --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/demo/securityDataFormatRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/businessObjectFormats --insecure"

    # Trade Data - Registering objects
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/demo/tradeDataObjectRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/businessObjectDefinitions --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/demo/tradeDataFormatRegistration.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/businessObjectFormats --insecure"

fi

echo "Everything looks good"

exit 0
