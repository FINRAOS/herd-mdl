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

# Deploy apache files
APACHE_ROOT=/etc/httpd
execute_cmd "cp -f  $APACHE_ROOT/conf/httpd.conf $APACHE_ROOT/conf/httpd.conf_orig"
execute_cmd "cp ${deployLocation}/conf/httpd.conf $APACHE_ROOT/conf/httpd.conf"

# Configure apache
execute_cmd "sed -i 's/^User .*/User apache/' $APACHE_ROOT/conf/httpd.conf"
execute_cmd "sed -i 's/^Group .*/Group apache/' $APACHE_ROOT/conf/httpd.conf"
execute_cmd "sed -i -e 's;^Listen .*;Listen 8888;' $APACHE_ROOT/conf/httpd.conf"

exit 0
