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

# export aws executable
export AWS_BIN=$(which aws 2> /dev/null)

function init() {

    echo "Initializing.."

    # source bash_profile to load logger and utils
    source ~/.bash_profile

    # source config file to load deployment variables
    configFile="/home/mdladmin/deploy/mdl/conf/deploy.props"
    if [ ! -f ${configFile} ]; then
        error "Config file does not exist ${configFile}"
        exit 1
    fi
    . ${configFile}

    # export executables
    echo "Exporting executables."
    export_execs

    # Fetch parameters
    export LDAP_ADMIN_USER=$(${AWS_BIN} ssm get-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/User/AdministratorName --output text --query Parameter.Value)
    export HERD_ADMIN_USER=$(${AWS_BIN} ssm get-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/User/HerdAdminUsername --output text --query Parameter.Value)
    export HERD_RO_USER=$(${AWS_BIN} ssm get-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/User/HerdRoUsername --output text --query Parameter.Value)

    export MDL_APP_USER=$(${AWS_BIN} ssm get-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/User/HerdMdlUsername --output text --query Parameter.Value)
    export SEC_APP_USER=$(${AWS_BIN} ssm get-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/User/HerdSecUsername --output text --query Parameter.Value)
    export BASIC_APP_USER=$(${AWS_BIN} ssm get-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/User/HerdBasicUsername --output text --query Parameter.Value)

    export PRINCIPLE_OU="ou=People"

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

    echo "Initializing params."
    # set params
    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/HostName --value ${HOSTNAME} --type String --description \"LDAP server hostname\" --overwrite"
    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/AuthGroup --value ${PRINCIPLE_OU} --type String --description \"LDAP server hostname\" --overwrite"

    LDAP_ADMIN_PASS=$(echo "$(date +%s.%N)-$(($RANDOM*$RANDOM))" | sha256sum | base64 | head -c 12)
    sleep 1

    HERD_ADMIN_PASS=$(echo "$(date +%s.%N)-$(($RANDOM*$RANDOM))" | sha256sum | base64 | head -c 12)
    HERD_RO_PASS=$(echo "$(date +%s.%N)-$(($RANDOM*$RANDOM))" | sha256sum | base64 | head -c 12)
    MDL_APP_PASS=$(echo "$(date +%s.%N)-$(($RANDOM*$RANDOM))" | sha256sum | base64 | head -c 12)
    SEC_APP_PASS=$(echo "$(date +%s.%N)-$(($RANDOM*$RANDOM))" | sha256sum | base64 | head -c 12)

    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/Password/AdministratorPassword --value ${LDAP_ADMIN_PASS} --type SecureString --description \"LDAP administrative password\" --overwrite"
    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/Password/HerdAdminPassword --value ${HERD_ADMIN_PASS} --type SecureString --description \"Herd admin user password\" --overwrite"
    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/Password/HerdRoPassword --value ${HERD_RO_PASS} --type SecureString --description \"Herd readonly user password\" --overwrite"
    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/Password/HerdMdlPassword --value ${MDL_APP_PASS} --type SecureString --description \"LDAP application/service password\" --overwrite"
    execute_cmd "${AWS_BIN} ssm put-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/Password/HerdSecPassword --value ${SEC_APP_PASS} --type SecureString --description \"LDAP sec account password\" --overwrite"

    echo "Generating passwords."
    LDAP_ADMIN_PASS_CRYPT=$(${SLAPPASSWD_BIN} -s "${LDAP_ADMIN_PASS}")
    HERD_ADMIN_PASS_CRYPT=$(${SLAPPASSWD_BIN} -s "${HERD_ADMIN_PASS}")
    HERD_RO_PASS_CRYPT=$(${SLAPPASSWD_BIN} -s "${HERD_RO_PASS}")
    MDL_APP_PASS_CRYPT=$(${SLAPPASSWD_BIN} -s "${MDL_APP_PASS}")
    SEC_APP_PASS_CRYPT=$(${SLAPPASSWD_BIN} -s "${SEC_APP_PASS}")
    BASIC_APP_PASS_CRYPT=$(${SLAPPASSWD_BIN} -s "${BASIC_APP_PASS}")

    BASE_DN=$(${AWS_BIN} ssm get-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/BaseDN --output text --query Parameter.Value)
}

# Configures TLS settings on the OpenLDAP server
function configure_ssl() {

    echo "Configuring TLS."

    # Enable SSL listener, LDAPS
    if [ $(grep "^SLAPD_LDAPS=no" /etc/sysconfig/ldap) ]; then
        sed -e 's/SLAPD_LDAPS=no/#SLAPD_LDAPS=no/' -i /etc/sysconfig/ldap
    fi
    if ! [ $(grep "^SLAPD_LDAPS=yes" /etc/sysconfig/ldap) ]; then
        echo 'SLAPD_LDAPS=yes' >> /etc/sysconfig/ldap
    fi

    echo "TLS_REQCERT allow" | tee -a /etc/openldap/ldap.conf

    service slapd restart
    sleep 5
}

# Configures logging
function configure_logging() {
    echo "Configuring logging."
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

        echo "Creating Group: ${1}"

        local GROUP_NAME="$1"
        local USER_NAME="$2"

        read -r -d '' CONF <<EOF

dn: cn=${GROUP_NAME},ou=Groups,${BASE_DN}
cn: ${GROUP_NAME}
objectClass: top
objectClass: groupOfNames
member: cn=${USER_NAME},${PRINCIPLE_OU},${BASE_DN}

EOF
        echo "${CONF}" > ${deployLocation}/conf/group.ldif

        ldapadd -H "ldaps://${HOSTNAME}" -x -D "cn=${LDAP_ADMIN_USER},${BASE_DN}" -w "${LDAP_ADMIN_PASS}" -f ${deployLocation}/conf/group.ldif

}

# Creates a new LDAP user
function create_user() {

    echo "Creating user with uid=${2}"

    local user_cn=$1
    local user_uid=$2
    local user_pwd_crypt=$3
    local uid=$4
    local gid=$5
    IFS=',' read -r -a array <<< "${BASE_DN}"
    local domain=$(echo "${array[0]}" | egrep -o 'dc=[0-9a-zA-Z]+' | head -1 | sed -e 's/dc=//g')
    local domainExt=$(echo "${array[1]}" | egrep -o 'dc=[0-9a-zA-Z]+' | head -1 | sed -e 's/dc=//g')

    read -r -d '' CONF <<EOF

dn: cn=${user_cn},${PRINCIPLE_OU},${BASE_DN}
changetype: add
objectClass: inetOrgPerson
objectClass: posixAccount
uid: ${user_cn}
cn: ${user_cn}
sn: null
userPassword: ${user_pwd_crypt}
uidNumber: ${uid}
gidNumber: ${gid}
homeDirectory: /home/${user_cn}
mail: ${user_cn}@${domain}.${domainExt}
loginShell: /bin/bash

EOF

  echo "${CONF}" > ${deployLocation}/conf/user.ldif

  ldapadd -H "ldaps://${HOSTNAME}" -x -D "cn=${LDAP_ADMIN_USER},${BASE_DN}" -w "${LDAP_ADMIN_PASS}" -f ${deployLocation}/conf/user.ldif

}

function add_user_to_group(){

  # Add LDAP user to LDAP group

  local GROUP="$1"
  local USER_NAME="$2"

  read -r -d '' CONF <<EOF
dn: cn=${GROUP},ou=Groups,${BASE_DN}
changetype: modify
add: member
member: cn=${USER_NAME},ou=People,${BASE_DN}
EOF
  echo "$CONF" > ${deployLocation}/conf/conf.ldif

  ldapmodify \
    -v \
    -x \
    -H "ldaps://${HOSTNAME}" \
    -D "cn=${LDAP_ADMIN_USER},${BASE_DN}" \
    -w "${LDAP_ADMIN_PASS}" \
    -f ${deployLocation}/conf/conf.ldif

}

function enable_memberof_overlay() {

  echo "Enabling memberof overlay"

  # Load memberof module
  ldapadd -Q -Y EXTERNAL -H ldapi:/// -f ${deployLocation}/conf/load_memberof.ldif

  # Add member of overlay
  ldapadd -Q -Y EXTERNAL -H ldapi:/// -f ${deployLocation}/conf/memberof_conf.ldif

  # Load refint module
  ldapadd -Q -Y EXTERNAL -H ldapi:/// -f ${deployLocation}/conf/load_refint.ldif

  # Add refint config
  ldapadd -Q -Y EXTERNAL -H ldapi:/// -f ${deployLocation}/conf/refint_conf.ldif

}

# Configures the OpenLDAP server
function configure_ldap() {

    # initialize the bdb with the bundled example
    cp /usr/share/openldap-servers/DB_CONFIG.example /var/lib/ldap/DB_CONFIG

    chown -R ldap:ldap /var/lib/ldap

    /etc/init.d/slapd start
    chkconfig slapd on

    sleep 5

    # navigate to openldap's config directory
    cd /etc/openldap/slapd.d/cn\=config

    # Add ldap admin user, root DN and Suffix.
    echo "Adding ldap admin user, root DN and Suffix."
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

# Make sure that non-admin users cannot see other users' password hash.
cat <<EOT >> olcDatabase\=\{2\}bdb.ldif
olcAccess: {0}to attrs=userPassword by self write by dn.base="cn=${LDAP_ADMIN_USER},${BASE_DN}" write by anonymous auth by * none
olcAccess: {1}to * by dn.base="cn=${LDAP_ADMIN_USER},${BASE_DN}" write by self write by * read
EOT

    # modify olcAccess
    sed -i '/olcAccess: /d' ./olcDatabase\=\{1\}monitor.ldif
    sed -i '/ nal,cn=auth" read/d' ./olcDatabase\=\{1\}monitor.ldif
    sed -i '/ one/d' ./olcDatabase\=\{1\}monitor.ldif

cat <<EOT >> olcDatabase\=\{1\}monitor.ldif
olcAccess: {0}to *  by dn.base="gidNumber=0+uidNumber=0,cn=peercred,cn=external,cn=auth" read  by dn.base="cn=${LDAP_ADMIN_USER},${BASE_DN}" read  by * none
EOT

    # restart service (there will be checksum warnings because we did dirty writes to config files but that's okay for now)
    echo "Starting slapd service after initial configuration"
    chkconfig slapd on
    service slapd restart
    sleep 5

    # Enable memberOf overlay
    enable_memberof_overlay

    # restart service (there will be checksum warnings because we did dirty writes to config files but that's okay for now)
    echo "Restarting slapd service after adding memberof overlay and referential integrity."
    chkconfig slapd on
    service slapd restart
    sleep 5

    # Add the entries for domain and Organizational Unit (OU), users and groups
    read -r -d '' CONF <<EOF

dn: ${BASE_DN}
objectClass: dcObject
objectClass: organization
dc: $(echo ${BASE_DN} | egrep -o 'dc=[0-9a-zA-Z]+' | head -1 | sed -e 's/dc=//g')
o: $(echo ${BASE_DN} | egrep -o '(dc|ou?)=[0-9a-zA-Z]+' | head -1 | sed -e 's/\(dc\|ou\?\)=//g')

dn: ou=People,${BASE_DN}
objectClass: organizationalUnit
ou: People

dn: ou=Groups,${BASE_DN}
objectClass: organizationalUnit
ou: Groups

EOF
    echo "${CONF}" > ${deployLocation}/conf/org_orgunits.ldif

    ldapadd -D "cn=${LDAP_ADMIN_USER},${BASE_DN}" -w "${LDAP_ADMIN_PASS}" -f ${deployLocation}/conf/org_orgunits.ldif


    # Verify information in debug logs
    echo "Running an ldapsearch on the baseDN.."
    ldapsearch -x -LLL -b "${BASE_DN}"

    # configure TLS
    configure_ssl

    # Add MDL Service account
    create_user "${MDL_APP_USER}" "${MDL_APP_USER}" "${MDL_APP_PASS_CRYPT}" 10002 1001

    # Add SEC Service account
    create_user "${SEC_APP_USER}" "${SEC_APP_USER}" "${SEC_APP_PASS_CRYPT}" 10003 1001

    # Add Herd Admin Service account
    create_user "${HERD_ADMIN_USER}" "${HERD_ADMIN_USER}" "${HERD_ADMIN_PASS_CRYPT}" 10004 1001

    # Add Herd Read-Only Service account
    create_user "${HERD_RO_USER}" "${HERD_RO_USER}" "${HERD_RO_PASS_CRYPT}" 10005 1001

    # Add Basic user Service account
    create_user "${BASIC_APP_USER}" "${BASIC_APP_USER}" "${BASIC_APP_PASS_CRYPT}" 10006 1001

    HERD_ADMIN_AUTH_GROUP=$(${AWS_BIN} ssm get-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/AuthGroup/Admin --output text --query Parameter.Value)
    RO_AUTH_GROUP=$(${AWS_BIN} ssm get-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/AuthGroup/RO --output text --query Parameter.Value)
    MDL_AUTH_GROUP=$(${AWS_BIN} ssm get-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/AuthGroup/MDL --output text --query Parameter.Value)
    SEC_AUTH_GROUP=$(${AWS_BIN} ssm get-parameter --name /app/MDL/${MDLInstanceName}/${Environment}/LDAP/AuthGroup/SEC --output text --query Parameter.Value)

    create_group "${HERD_ADMIN_AUTH_GROUP}" "${HERD_ADMIN_USER}"
    create_group "${RO_AUTH_GROUP}" "${HERD_RO_USER}"
    create_group "${MDL_AUTH_GROUP}" "${MDL_APP_USER}"
    create_group "${SEC_AUTH_GROUP}" "${SEC_APP_USER}"

    #Create group APP_MDL_Users and add users
    create_group "APP_MDL_Users" "${MDL_APP_USER}"
    add_user_to_group "APP_MDL_Users" "${SEC_APP_USER}"
    add_user_to_group "APP_MDL_Users" "${HERD_RO_USER}"
    add_user_to_group "APP_MDL_Users" "${HERD_ADMIN_USER}"

    #Add admin user to namesapce corresponding groups
    add_user_to_group "${MDL_AUTH_GROUP}" "${HERD_ADMIN_USER}"
    add_user_to_group "${SEC_AUTH_GROUP}" "${HERD_ADMIN_USER}"

    #Add basic user to readOnly group
    add_user_to_group "${RO_AUTH_GROUP}" "${BASIC_APP_USER}"

    # Verify memberOf in debug logs
    echo "Running an ldapsearch to verify memberOf overlay."
    ldapsearch -x -LLL -H ldap:/// -b "cn=${MDL_APP_USER},${PRINCIPLE_OU},${BASE_DN}" dn memberof

    execute_cmd "service slapd restart"
}

init
configure_logging
configure_ldap

# signal success to the cloudformation wait handle
execute_cmd "/opt/aws/bin/cfn-signal -e 0 -r 'Open LDAP Installation Complete' \"${waitHandleForLdap}\""
