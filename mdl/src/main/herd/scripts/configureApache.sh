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

# Install and Configure Apache
execute_cmd "wget --quiet --random-wait https://archive.apache.org/dist/tomcat/tomcat-connectors/jk/binaries/linux/jk-1.2.31/x86_64/mod_jk-1.2.31-httpd-2.2.x.so -O /usr/lib64/httpd/modules/mod_jk.so"
execute_cmd " echo y | cp -f ${deployLocation}/conf/httpd.conf /etc/httpd/conf/httpd.conf"
execute_cmd " echo y | cp -f ${deployLocation}/conf/ssl.conf /etc/httpd/conf.d/ssl.conf"
execute_cmd " echo y | cp -f ${deployLocation}/conf/workers.properties /etc/tomcat8/workers.properties"
execute_cmd "mkdir -p /home/mdladmin/certs/"
execute_cmd "openssl genrsa -out /home/mdladmin/certs/key.pem 2048"
execute_cmd "openssl req -new -sha256 -key /home/mdladmin/certs/key.pem -out /home/mdladmin/certs/csr.csr  < ${deployLocation}/conf/domainConfig"
execute_cmd "openssl req -x509 -sha256 -days 365 -key /home/mdladmin/certs/key.pem -in /home/mdladmin/certs/csr.csr -out /home/mdladmin/certs/certificate.pem < ${deployLocation}/conf/domainConfig"
openssl req -in /home/mdladmin/certs/csr.csr -text -noout | grep -i "Signature.*SHA256" && echo "All is well" || echo "This certificate will stop working in 2017! You must update OpenSSL to generate a widely-compatible certificate"
execute_cmd "chown -R mdladmin:mdladmin /home/mdladmin/certs"

if [ "${enableSSLAndAuth}" = "true" ] ; then
    execute_cmd "sed -i \"s~{LDAP_URL}~ldaps://${ldapHostName}/${ldapBaseDN}~g\" ${deployLocation}/conf/httpdForLdap.conf"
    execute_cmd "cat ${deployLocation}/conf/httpdForLdap.conf >> /etc/httpd/conf/httpd.conf"
else
    execute_cmd "cat ${deployLocation}/conf/httpdNoAuth.conf >> /etc/httpd/conf/httpd.conf"
fi

exit 0
