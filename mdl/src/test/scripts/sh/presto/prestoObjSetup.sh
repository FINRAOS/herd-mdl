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
	cmd="${1} --retry 5 --max-time 120 --retry-delay 7 --write-out \"\nHTTP_CODE:%{http_code}\n\" -u ${ldapAppUsername}:${ldapAppPassword}"
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
        cmd="java -jar ./mdl/build/lib/herd-uploader-app.jar --force -e s3-external-1.amazonaws.com -l ./mdlt/inputs/data/herd/${2}/ -m ${1} -V -H ${HerdLoadBalancerDNSName} ${port} -R 3 -D 60"
        echo $cmd
        cmdWithCredentials="${cmd} -u ${ldapAppUsername} -w ${ldapAppPassword}"
        eval $cmdWithCredentials
        check_error ${PIPESTATUS[0]} "$cmd"
}

function replaceXmlFile {
    execute_cmd "cp ./mdlt/inputs/rest/herd/object/test_data/${1} body.xml"
    execute_cmd "sed -i \"s/{{namespace}}/${namespace}/g\" body.xml"
    execute_cmd "sed -i \"s/{{objectname}}/${objectname}/g\" body.xml"
}

function replaceManifestFile {
    execute_cmd "cp ./mdlt/inputs/data/herd/${1} 2017-08-01.manifest.json"
    execute_cmd "sed -i \"s/{{namespace}}/${namespace}/g\" 2017-08-01.manifest.json"
    execute_cmd "sed -i \"s/{{objectname}}/${objectname}/g\" 2017-08-01.manifest.json"
}

#MAIN
namespace=$1
objectname=$2
createNamespace=$3

configFile="/home/ec2-user/deployHost.props"
testConfigFile="/home/ec2-user/mdlt/conf/test.props"

if [ ! -f ${configFile} ] ; then
    echo "Config file does not exist ${configFile}"
    exit 1
fi
. ${configFile}
. ${testConfigFile}

PrestoClusterId=${BdsqlEMRPrestoCluster}
ldapAppUserSsmKey="/mdl/ldap/app_user"
ldapAppUserPwdSsmKey="/mdl/ldap/app_pass"

execute_cmd "cd /home/ec2-user"
execute_cmd "aws configure set default.region ${RegionName}"

ldapAppUsername=$(aws ssm get-parameter --name "${ldapAppUserSsmKey}" --output text --query 'Parameter.Value')
ldapAppPassword=$(aws ssm get-parameter --name  "${ldapAppUserPwdSsmKey}" --with-decryption --output text --query 'Parameter.Value')

# Set the port and SSL information for uploader based on https or http
if [ "${EnableSSLAndAuth}" = "true" ] ; then
    export port="-P 443 -s true"
else
    export port="-P 80"
fi

#create namespace
if [ "${createNamespace}" = "true" ] ; then
    replaceXmlFile "namespaceRegistration.xml"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @body.xml -X POST ${HerdLoadBalancerURL}/herd-app/rest/namespaces --insecure"
fi

#create herd test Object definition & format under existing namespace: SEC_MARKET_DATA
replaceXmlFile "testDataObjectRegistration.xml"
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @body.xml -X POST ${HerdLoadBalancerURL}/herd-app/rest/businessObjectDefinitions --insecure"

replaceXmlFile "testDataFormatRegistration.xml"
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @body.xml -X POST ${HerdLoadBalancerURL}/herd-app/rest/businessObjectFormats --insecure"

#register Herd Notification to notify hiveCluster
replaceXmlFile "testDataObjectNotificationRegistration.xml"
execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @body.xml -X POST ${HerdLoadBalancerURL}/herd-app/rest/notificationRegistrations/businessObjectDataNotificationRegistrations --insecure"

#upload herd data, this will auto create hive cluster
replaceManifestFile "2017-08-01.manifest.json"
execute_uploader_cmd "2017-08-01.manifest.json" "2017-08-01"

echo "wait for the hive cluster created"
execute_cmd "sleep 1m"

echo "wait for the hive cluster running"
namespace="MDL"
emrClusterDefinitionName="MDLMetastorHiveCluster"
emrClusterName="${MDLInstanceName}_Cluster"
#herd call to get hiveClusterId using cluster Name
execute_curl_cmd "curl -H 'Content-Type: application/xml' -X GET ${HerdLoadBalancerURL}/herd-app/rest/emrClusters/namespaces/${namespace}/emrClusterDefinitionNames/${emrClusterDefinitionName}/emrClusterNames/${emrClusterName} --insecure"
hiveClusterId=`cat /tmp/curlCmdOutput | sed -e 's/HTTP_CODE\:.*//g' | grep -o -P '(?<=\<id>)[^\/]*(?=\<\/id>)' | head -n 1`
execute_cmd "aws emr wait cluster-running --cluster-id ${hiveClusterId}"