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
#!/bin/bash -x

function init() {
    # source config file to load deployment variables
    configFile="/home/mdladmin/deploy/mdl/conf/deploy.props"
    if [ ! -f ${configFile} ]; then
        error "Config file does not exist ${configFile}"
        exit 1
    fi
    . ${configFile}

    # export constants
    export LDAP_ADMIN_USER="ldap_admin_user"
    export MDL_APP_USER="ldap_mdl_app_user"
    export SEC_APP_USER="ldap_sec_app_user"
    export AUTH_GROUP="ou=People"

    # source bash_profile to load logger and utils
    source ~/.bash_profile

     # export executables
    export_execs

    # fetch SSM parameters
    init_params
}

# Configure a handle on useful executables
function export_execs() {
    export AWS_BIN=$(which aws 2> /dev/null)
    export SLAPPASSWD_BIN=$(which slappasswd 2> /dev/null)
    execute_cmd "${AWS_BIN} configure set default.region ${region}"
}

# Initializes (fetches) parameters from SSM
function init_params() {
    # set params
    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/HostName --value ${HOSTNAME} --type String --description \"LDAP server hostname\" --overwrite"
    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/AuthGroup --value ${AUTH_GROUP} --type String --description \"LDAP server hostname\" --overwrite"

    LDAP_ADMIN_PASS=$(echo "$(date +%s.%N)-$(($RANDOM*$RANDOM))" | sha256sum | base64 | head -c 12)
    sleep 1

    MDL_APP_PASS=$(echo "$(date +%s.%N)-$(($RANDOM*$RANDOM))" | sha256sum | base64 | head -c 12)
    SEC_APP_PASS=$(echo "$(date +%s.%N)-$(($RANDOM*$RANDOM))" | sha256sum | base64 | head -c 12)

    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/AdministratorPassword --value ${LDAP_ADMIN_PASS} --type SecureString --description \"LDAP administrative password\" --overwrite"
    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/MDLAppPassword --value ${MDL_APP_PASS} --type SecureString --description \"LDAP application/service password\" --overwrite"
    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/SecAppPassword --value ${SEC_APP_PASS} --type SecureString --description \"LDAP sec account password\" --overwrite"

    LDAP_ADMIN_PASS_CRYPT=$(${SLAPPASSWD_BIN} -s "${LDAP_ADMIN_PASS}")
    MDL_APP_PASS_CRYPT=$(${SLAPPASSWD_BIN} -s "${MDL_APP_PASS}")
    SEC_APP_PASS_CRYPT=$(${SLAPPASSWD_BIN} -s "${SEC_APP_PASS}")

    BASE_DN=$(${AWS_BIN} ssm get-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/BaseDN --output text --query Parameter.Value)
}

# Configures TLS settings on the OpenLDAP server
function configure_ssl() {

    # Enable SSL listener, LDAPS
    if [ $(grep "^SLAPD_LDAPS=no" /etc/sysconfig/ldap) ]; then
        sed -e 's/SLAPD_LDAPS=no/#SLAPD_LDAPS=no/' -i /etc/sysconfig/ldap
    fi
    if ! [ $(grep "^SLAPD_LDAPS=yes" /etc/sysconfig/ldap) ]; then
        echo 'SLAPD_LDAPS=yes' >> /etc/sysconfig/ldap
    fi

    echo "TLS_REQCERT allow" | tee -a /etc/openldap/ldap.conf
}

# Configures logging
function configure_logging() {

    # Setup ldap logging
    cat > /etc/rsyslog.d/ldap.conf << EOF
#Logging for slapd and logrotate
local4.*    /var/log/ldap
EOF

    /etc/init.d/rsyslog restart

    cat > /etc/logrotate.d/ldap << EOF
/var/log/ldap
{
    sharedscripts
    postrotate
        /bin/kill -HUP `cat /var/run/syslogd.pid 2> /dev/null` 2> /dev/null || true
    endscript
}
EOF

}

# Create new LDAP group
function create_group(){

        local GROUP_NAME="$1"
        local USER_NAME="$2"

        read -r -d '' CONF <<EOF
dn: cn=${GROUP_NAME},ou=Groups,${BASE_DN}
objectClass: top
objectClass: groupOfNames
member: uid=${USER_NAME},${AUTH_GROUP},${BASE_DN}
EOF
        echo "${CONF}" > group.ldif

        ldapadd -H "ldaps://${HOSTNAME}" -x -D "cn=${LDAP_ADMIN_USER},${BASE_DN}" -w "${LDAP_ADMIN_PASS}" -f group.ldif

}

# Configures the OpenLDAP server
function configure_ldap() {

    # initialize the bdb with the bundled example
    cp /usr/share/openldap-servers/DB_CONFIG.example /var/lib/ldap/DB_CONFIG

    chown -R ldap:ldap /var/lib/ldap

    /etc/init.d/slapd start
    chkconfig slapd on

    sleep 5

    ldapmodify -a -Q -Y EXTERNAL -H ldapi:/// << EOF
dn: olcDatabase={0}config,cn=config
changetype: modify
add: olcRootPW
olcRootPW: ${LDAP_ADMIN_PASS_CRYPT}

dn: olcDatabase={2}bdb,cn=config
changetype: modify
add: olcRootPW
olcRootPW: ${LDAP_ADMIN_PASS_CRYPT}
-
replace: olcRootDN
olcRootDN: cn=${LDAP_ADMIN_USER},${BASE_DN}
-
replace: olcSuffix
olcSuffix: ${BASE_DN}
EOF

    ldapadd -x -w "${LDAP_ADMIN_PASS}" -D "cn=${LDAP_ADMIN_USER},${BASE_DN}" -H ldapi:/// << EOF
dn: ${BASE_DN}
objectclass: dcObject
objectclass: organization
o: `echo ${BASE_DN} | sed -e 's/,dc=/./g' -e 's/dc=//'`

dn: ${AUTH_GROUP},${BASE_DN}
objectClass: top
objectClass: organizationalUnit
ou: People

dn: ou=Groups,${BASE_DN}
objectClass: top
objectClass: organizationalUnit
ou: Groups
EOF

    # add MDL service account
    read -r -d '' USER_LDIF << EOF
dn: uid=${MDL_APP_USER},${AUTH_GROUP},${BASE_DN}
changetype: add
uid: ${MDL_APP_USER}
cn: ${MDL_APP_USER}
sn: null
objectClass: inetOrgPerson
userPassword: ${MDL_APP_PASS_CRYPT}
EOF
    echo "${USER_LDIF}" > new_user.ldif
    ldapmodify -x -w "${LDAP_ADMIN_PASS}" -D "cn=${LDAP_ADMIN_USER},${BASE_DN}" -H "ldaps://${HOSTNAME}" -f new_user.ldif

    # add MDL test account
    read -r -d '' USER_LDIF << EOF
dn: uid=${SEC_APP_USER},${AUTH_GROUP},${BASE_DN}
changetype: add
uid: ${SEC_APP_USER}
cn: ${SEC_APP_USER}
sn: null
objectClass: inetOrgPerson
userPassword: ${SEC_APP_PASS_CRYPT}
EOF
    echo "$USER_LDIF" > new_user.ldif
    ldapmodify -x -w "${LDAP_ADMIN_PASS}" -D "cn=${LDAP_ADMIN_USER},${BASE_DN}" -H "ldaps://${HOSTNAME}" -f new_user.ldif

    # Create MDL LDAP group and add service account to group
    MDL_AD="APP_MDL_ACL_RO_mdl"
    SEC_AD="APP_MDL_ACL_RO_sec_market_data"
    create_group "${MDL_AD}" "${MDL_APP_USER}"
    create_group "${SEC_AD}" "${SEC_APP_USER}"

    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/MdlAdGroup --value "${MDL_AD}" --type String --description \"LDAP mdl schema AD group\" --overwrite"
    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/SecAdGroup --value "${SEC_AD}" --type String --description \"LDAP sec_market_data schema AD group\" --overwrite"

    execute_cmd "/etc/init.d/slapd restart"
}

init
configure_ssl
configure_logging
configure_ldap

# signal success to the cloudformation wait handle
execute_cmd "/opt/aws/bin/cfn-signal -e 0 -r 'Open LDAP Installation Complete' \"${waitHandleForLdap}\""
