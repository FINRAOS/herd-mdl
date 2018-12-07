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

# export configurations
configFile="/home/mdladmin/deploy/mdl/conf/deploy.props"
if [ ! -f ${configFile} ] ; then
    echo "Config file does not exist ${configFile}"
    exit 1
fi
. ${configFile}

# Get admin user and password from parameter store
herdAdminUsername=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/User/HerdAdminUsername --region ${region} --output text --query Parameter.Value)
herdAdminPassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/Password/HerdAdminPassword --with-decryption --region ${region} --output text --query Parameter.Value)
requestedHerdVersion=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/HERD/RequestedVersion --region ${region} --output text --query Parameter.Value)
rollingDeployment=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/HERD/DeploymentInvoked --region ${region} --output text --query Parameter.Value)

execute_cmd "aws configure set default.region ${region}"

# Global variable to hold output from cURL commands
RESPONSE_HOLDER=""

# Execute cURL command for Herd REST operations
function execute_herd_command {
    url=${1}
    http_verb=${2}
    echo "Running ${http_verb} on endpoint: ${httpProtocol}://localhost/herd-app/rest/${1}"
    RESPONSE_HOLDER=$(curl --insecure --silent --user ${herdAdminUsername}:${herdAdminPassword} -X ${http_verb} -H "Accept: application/json" -H "Content-Type: application/json" ${httpProtocol}://localhost/herd-app/rest/${1})
}

echo "Running smoke tests..."

# Verifies if the requested Herd version was installed
function verify_build_number {
  echo "Verifying build number, requested version is: ${requestedHerdVersion}"
  execute_herd_command "buildInfo" "GET"
  build_number=`echo ${RESPONSE_HOLDER} | jq -r .buildNumber`
  echo "Actual build number: ${build_number}"

  if [ "${build_number}" = "${requestedHerdVersion}" ]; then
      echo "Verified build number."
  else
      echo "Actual herd version: ${build_number} doesn't match requested version: ${requestedHerdVersion}"
      exit 1
  fi
}

# Verifies if exactly both the demo namespaces have been registered
function verify_namespaces_get {
  echo "Verifying namepsaces GET. Expected namespaces are: [mdl, sec_market_data]."
  execute_herd_command "namespaces" "GET"
  num_namespaces=`echo ${RESPONSE_HOLDER} | jq -r '.namespaceKeys | length'`
  echo "number: ${num_namespaces}"

  if [ "${num_namespaces}" -ge "2" ]; then
      echo "Verified number of namespaces."
  else
      echo "Actual number of namespaces found: ${num_namespaces}, was expecting at least 2."
      exit 1
  fi

  actual_namespaces=`echo ${RESPONSE_HOLDER} | jq -r '.namespaceKeys' | jq -r '.[].namespaceCode'`

  if [[ "${actual_namespaces}" =~ "MDL" ]]; then
     echo "Verified namespace: 'MDL' exists."
  else
     echo "ERROR: Namespace 'MDL' not found."
     exit 1
  fi

  if [[ "${actual_namespaces}" =~ "SEC_MARKET_DATA" ]]; then
     echo "Verified namespace: 'SEC_MARKET_DATA' exists."
  else
     echo "ERROR: Namespace 'SEC_MARKET_DATA' not found."
     exit 1
  fi

}

## Execute smoke tests
# Verify the requested Herd version only when performing a rolling upgrade
if [ "${rollingDeployment}" = "true" ]; then
    verify_build_number
fi

verify_namespaces_get

if [ "${rollingDeployment}" = "true" ]; then
    ## Reset the deployment 'state' after validating Herd deployment
    execute_cmd "aws ssm put-parameter --name /app/MDL/${mdlInstanceName}/${environment}/HERD/RequestedVersion --type String --value FilledLater --region ${region} --overwrite"
    execute_cmd "aws ssm put-parameter --name /app/MDL/${mdlInstanceName}/${environment}/HERD/CurrentVersion --type String --value ${requestedHerdVersion} --region ${region} --overwrite"
    execute_cmd "aws ssm put-parameter --name /app/MDL/${mdlInstanceName}/${environment}/HERD/DeploymentInvoked --type String --value false --region ${region} --overwrite"
fi