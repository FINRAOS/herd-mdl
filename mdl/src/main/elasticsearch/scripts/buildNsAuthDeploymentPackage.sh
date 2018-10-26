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

echo "Preparing lambda deployment package."

echo "Installing OS packages and utilities."
execute_cmd "yum -y install zip gcc-c++ cc1plus python-pip python-devel cyrus-sasl-devel"
execute_cmd "mkdir -p build && cd build/"

echo "Installing required python packages."
execute_cmd "pip install thriftpy sqlalchemy -t ."
execute_cmd "pip install sasl -t ."
execute_cmd "pip install requests -t ."
execute_cmd "pip install six -t ."
execute_cmd "pip install bit_array -t ."
execute_cmd "pip install thrift_sasl==0.2.1 -t ."
execute_cmd "pip install pure-sasl -t ."
execute_cmd "pip install impyla -t ."
execute_cmd "pip install boto3 -t ."
execute_cmd "pip install botocore -t ."

echo "Bundling everything into a zip file"
execute_cmd "zip -r ns_auth_sync_utility.zip ."

