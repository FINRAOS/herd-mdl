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
deployPropertiesFile=$1
testPropsFile=$2

# Source the properties
. ${deployPropertiesFile}
execute_cmd "cd /home/ec2-user"

#mdlt setup: bring up mdl stack
execute_cmd "java -DDeployPropertiesFile=$deployPropertiesFile -cp mdlt/herd-mdl-${ReleaseVersion}-tests.jar:mdlt/mdlt-dependencies-${ReleaseVersion}.jar org.tsi.mdlt.util.TestWrapper setup"

#source test properties(stack output properties)
. ${testPropsFile}
#Copy ldap certificates to mdlt deploy host
if [ "${EnableSSLAndAuth}" = 'true' ] ; then
    #1. add LDAP certificate to trusted store
    execute_cmd "aws configure set default.region ${RegionName}"
    LDAP_SERVER=$(aws ssm get-parameter --name "/app/MDL/${MDLInstanceName}/${Environment}/LDAP/HostName" --output text --query Parameter.Value)
    LDAP_BASE_DN=$(aws ssm get-parameter --name "/app/MDL/${MDLInstanceName}/${Environment}/LDAP/BaseDN" --output text --query Parameter.Value)

    # export LDAP server cert
    echo | openssl s_client -connect "$LDAP_SERVER:636" | sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > ldapserver.pem
    # convert LDAP server cert from pem to der
    openssl x509 -outform der -in ldapserver.pem -out ldapserver.der
    # import LDAP server cert into Java truststore
    sudo keytool -import -alias mdl-ldap --keystore /usr/lib/jvm/jre/lib/security/cacerts --storepass changeit -file ldapserver.der -noprompt
    # remove temporary LDAP certs
    rm -fr ldapserver.pem ldapserver.der

    #2. copy certs jks to mdlt deploy host
    MDL_STAGING_BUCKET=$(aws ssm get-parameter --name "/app/MDL/${MDLInstanceName}/${Environment}/S3/MDL" --output text --query Parameter.Value)
    execute_cmd "aws s3 cp s3://${MDL_STAGING_BUCKET}/certs/mdl.jks certs.jks"
fi

# download herd uploader jar
execute_cmd "rm -rf mdl"
execute_cmd "mkdir -p mdl/herd"
#TODO need to remove this once herd issue fixed
herdVersion="0.72.0"
execute_cmd "wget --quiet --random-wait http://central.maven.org/maven2/org/finra/herd/herd-uploader/${herdVersion}/herd-uploader-${herdVersion}.jar -O ./mdl/herd/herd-uploader-app.jar"

# download bdsql sql_auth.sh and upload to mdlt s3 in order to be used for testing
execute_cmd "mkdir -p mdl/bdsql"
BdsqlReleaseVersion='1.1.0'
execute_cmd "wget --quiet --random-wait https://github.com/FINRAOS/herd-mdl/releases/download/bdsql-v${BdsqlReleaseVersion}/bdsql-${BdsqlReleaseVersion}-dist.zip -O bdsql.zip"
execute_cmd "unzip -q bdsql.zip -d ./mdl/bdsql"
execute_cmd "rm -rf bdsql.zip"
execute_cmd "aws s3 cp ./mdl/bdsql/scripts/sql_auth.sh s3://${MdltBucketName}/scripts/sh/presto/sql_auth.sh"

exit 0
