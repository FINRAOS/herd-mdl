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
	cmd="${1} --retry 5 --max-time 120 --retry-delay 7 --write-out \"\nHTTP_CODE:%{http_code}\n\" -u mdluser:mdl123"
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
        cmd="java -jar ${InputFilesDir}/lib/herd-uploader-app.jar -e s3-external-1.amazonaws.com -l ${InputFilesDir}/resources/demo-data/${2}/ -m ${InputFilesDir}/resources/demo-data/${1} -V -H ${HerdLoadBalancerDNSName} -P 443 -s true -R 3 -D 60 "
        echo $cmd
        cmdWithCredentials="${cmd} -u mdluser -w mdl123"
        eval $cmdWithCredentials
        check_error ${PIPESTATUS[0]} "$cmd"
}

execute_cmd "echo \"From $0\""

execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${InputFilesDir}/scripts/xml/demo/herd/marketDataNamespaceRegistration.xml -X POST https://${HerdLoadBalancerDNSName}:443/herd-app/rest/namespaces --insecure"
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${InputFilesDir}/scripts/xml/demo/herd/dataProvider.xml -X POST https://${HerdLoadBalancerDNSName}:443/herd-app/rest/dataProviders --insecure"
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${InputFilesDir}/scripts/xml/demo/herd/partitionKeyGroup.xml -X POST https://${HerdLoadBalancerDNSName}:443/herd-app/rest/partitionKeyGroups --insecure"

# Money Market Data - Registering objects
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${InputFilesDir}/scripts/xml/demo/herd/moneyMarketDataObjectRegistration.xml -X POST https://${HerdLoadBalancerDNSName}:443/herd-app/rest/businessObjectDefinitions --insecure"
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${InputFilesDir}/scripts/xml/demo/herd/moneyMarketDataFormatRegistration.xml -X POST https://${HerdLoadBalancerDNSName}:443/herd-app/rest/businessObjectFormats --insecure"

# Security Data - Registering objects
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${InputFilesDir}/scripts/xml/demo/herd/securityDataObjectRegistration.xml -X POST https://${HerdLoadBalancerDNSName}:443/herd-app/rest/businessObjectDefinitions --insecure"
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${InputFilesDir}/scripts/xml/demo/herd/securityDataFormatRegistration.xml -X POST https://${HerdLoadBalancerDNSName}:443/herd-app/rest/businessObjectFormats --insecure"

# Trade Data - Registering objects
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${InputFilesDir}/scripts/xml/demo/herd/tradeDataObjectRegistration.xml -X POST https://${HerdLoadBalancerDNSName}:443/herd-app/rest/businessObjectDefinitions --insecure"
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${InputFilesDir}/scripts/xml/demo/herd/tradeDataFormatRegistration.xml -X POST https://${HerdLoadBalancerDNSName}:443/herd-app/rest/businessObjectFormats --insecure"

# Registering metastor workflow
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${InputFilesDir}/scripts/xml/demo/metastor/securityDataObjectNotificationRegistration.xml -X POST https://${HerdLoadBalancerDNSName}:443/herd-app/rest/notificationRegistrations/businessObjectDataNotificationRegistrations --insecure"
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${InputFilesDir}/scripts/xml/demo/metastor/tradeDataObjectNotificationRegistration.xml -X POST https://${HerdLoadBalancerDNSName}:443/herd-app/rest/notificationRegistrations/businessObjectDataNotificationRegistrations --insecure"

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
