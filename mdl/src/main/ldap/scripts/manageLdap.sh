#!/bin/bash
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

function usage {

  # Display usage help

  SCRIPT=$(basename $(echo "$0"))
  read -r -d '' USAGE <<EOF
Usage:
  $SCRIPT --action [create_user|create_group|add_attribute|replace_attribute|add_user_to_group|remove_user_from_group|delete_user|delete_group|delete_object|show_directory] [--user name] [--password password] [--group name] [--dn distinguishedname]

Examples:
  $SCRIPT --action create_user --user userA --password password --email test@gmail.com --phone 1-234-567-8901
  $SCRIPT --action add_attribute --user userA --attribute telephoneNumber --value 1-234-567-8901
  $SCRIPT --action replace_attribute --user userA --attribute mail --value new@gmail.com
  $SCRIPT --action create_group --user userA --group groupA
  $SCRIPT --action add_user_to_group --user userB --group groupA
  $SCRIPT --action remove_user_from_group --user userB --group groupA
  $SCRIPT --action delete_object --dn "cn=userA,ou=People,ou=Groups,dc=domain,dc=com"
  $SCRIPT --action delete_user --user userA
  $SCRIPT --action delete_group --group groupA
  $SCRIPT --action show_directory
  $SCRIPT --help
EOF
  echo "$USAGE"
  exit 1
}


function install_deps(){

  # Install dependencies

  if [[ $(rpm -q jq | grep 'not installed' | wc -l) -eq 1 ]]; then
    echo "Installing jq ..."
    sudo yum install jq -y
  fi

}

function parse_args(){

  # Parse command line arguments

  while [[ $# > 1 ]]; do
    KEY="$1"
    case "$KEY" in
      --action)
      export ACTION="$2"
      shift
      ;;
      --user)
      export USER_NAME="$2"
      shift
      ;;
      --password)
      export PASSWORD="$2"
      shift
      ;;
      --email)
      export EMAIL="$2"
      shift
      ;;
      --phone)
      export PHONE="$2"
      shift
      ;;
      --attribute)
      export ATTRIBUTE="$2"
      shift
      ;;
      --value)
      export VALUE="$2"
      shift
      ;;
      --group)
      export GROUP="$2"
      shift
      ;;
      --dn)
      export DN="$2"
      shift
      ;;
      *)
      # unknown option
      ;;
    esac
  shift
  done
}

function init_ldap_info(){

  # Initialize OpenLDAP connection settings

  TAGS=$(get_tags)

  PURPOSE=$(echo "$TAGS" | jq -r 'select(.Key=="Purpose").Value')
  ENVIRONMENT=$(echo "$TAGS" | jq -r 'select(.Key=="Environment").Value')

  if [[ -z "$PURPOSE" || -z "$ENVIRONMENT" ]]; then
    echo "FATAL: Purpose and/or Environment tags are missing from ec2 instance."
    echo "$TAGS"
    exit 1
  fi

  LDAP_HOSTNAME=$(aws ssm get-parameter \
    --name "/app/MDL/$PURPOSE/$ENVIRONMENT/LDAP/HostName" \
    --output text --query Parameter.Value)
  BASE_DN=$(aws ssm get-parameter \
    --name "/app/MDL/$PURPOSE/$ENVIRONMENT/LDAP/BaseDN" \
    --output text --query Parameter.Value)

  ADMIN_USER=$(aws ssm get-parameter \
    --name "/app/MDL/$PURPOSE/$ENVIRONMENT/LDAP/User/AdministratorName" \
    --output text --query Parameter.Value)
  ADMIN_PASS=$(aws ssm get-parameter \
    --name "/app/MDL/$PURPOSE/$ENVIRONMENT/LDAP/Password/AdministratorPassword" \
    --with-decryption --output text --query Parameter.Value)
  APP_USER=$(aws ssm get-parameter \
    --name "/app/MDL/$PURPOSE/$ENVIRONMENT/LDAP/User/HerdAdminUsername" \
    --output text --query Parameter.Value)
  APP_PASS=$(aws ssm get-parameter \
    --name "/app/MDL/$PURPOSE/$ENVIRONMENT/LDAP/Password/HerdAdminPassword" \
    --with-decryption --output text --query Parameter.Value)

  if [[ -z "$LDAP_HOSTNAME" || -z "$BASE_DN" || -z "$ADMIN_USER" || -z "$ADMIN_PASS" || -z "$APP_USER" || -z "$APP_PASS" ]]; then
    echo "FATAL: Unable to retrieve LDAP connection settings from SSM."
    echo
    echo "Hostname: $LDAP_HOSTNAME"
    echo "Base DN: $BASE_DN"
    echo "Admin User: $ADMIN_USER"
    echo "Admin Password: $(echo "$ADMIN_PASS" | sed -e 's/./*/g')"
    echo "App User: $APP_USER"
    echo "App Password: $(echo "$APP_PASS" | sed -e 's/./*/g')"
    exit 1
  fi

}

function get_tags(){

  # Get ec2 instance tags

  INSTANCE_ID=$(curl \
    --silent http://169.254.169.254/latest/meta-data/instance-id)
  TAGS=$(aws ec2 describe-tags \
    --filter "Name=resource-id,Values=$INSTANCE_ID")

  echo "$TAGS" | jq -r '.Tags[]'

}

function get_next_uid(){

  # Get next avaiable uid

 maxUid=`ldapsearch \
    -x \
    -H "ldaps://$LDAP_HOSTNAME" \
    -D "cn=$ADMIN_USER,$BASE_DN" \
    -w "$ADMIN_PASS" \
   -b "ou=People,$BASE_DN" | grep uidNumber | awk -F ':' '{print $2}' | awk '{$1=$1;print}' | sort --version-sort | tail -n 1`
maxUid=$(( maxUid + 1 ))
echo "$maxUid"
}

function show_directory(){

  # Display LDAP directory
 ldapsearch \
    -x \
    -H "ldaps://$LDAP_HOSTNAME" \
    -D "cn=$ADMIN_USER,$BASE_DN" \
    -w "$ADMIN_PASS" \
   -b "$BASE_DN"
}



function create_user(){

  # Create LDAP user


  local USER_NAME="$1"
  local PASSWORD="$2"
  local EMAIL="$3"
  local PHONE="$4"
  if [[ "$EMAIL" = "" ]]; then
      EMAIL="$USER_NAME@cloudfjord.com"
  fi
  local next_uid=$(get_next_uid)
  local password_crypt=$(slappasswd -s "$PASSWORD")
if [[ -z "$USER_NAME" ]]; then
    usage
fi

  read -r -d '' CONF <<EOF
dn: cn=$USER_NAME,ou=People,$BASE_DN
changetype: add
uid: $USER_NAME
cn: $USER_NAME
sn: null
objectClass: inetOrgPerson
objectClass: posixAccount
userPassword: $password_crypt
uidNumber: $next_uid
gidNumber: 1001
homeDirectory: /home/$USER_NAME
mail: $EMAIL
loginShell: /bin/bash
EOF
  echo "$CONF" > conf.ldif

  if [[ ! -z "$PHONE" ]]; then
      echo "telephoneNumber:$PHONE" >> conf.ldif
  fi

  ldapmodify \
    -v \
    -x \
    -H "ldaps://$LDAP_HOSTNAME" \
    -D "cn=$ADMIN_USER,$BASE_DN" \
    -w "$ADMIN_PASS" \
    -f conf.ldif
  rm -fr conf.ldif
}

function modify_user(){

  # Modify LDAP user
  local action="$1"
  local USER_NAME="$2"
  local ATTRIBUTE="$3"
  local VALUE="$4"
if [[ -z "$USER_NAME" ]]; then
    usage
fi

  read -r -d '' CONF <<EOF
dn: cn=$USER_NAME,ou=People,$BASE_DN
changetype: modify
$action: $ATTRIBUTE
$ATTRIBUTE: $VALUE
EOF
  echo "$CONF" > conf.ldif
  ldapmodify \
    -v \
    -x \
    -H "ldaps://$LDAP_HOSTNAME" \
    -D "cn=$ADMIN_USER,$BASE_DN" \
    -w "$ADMIN_PASS" \
    -f conf.ldif
  rm -fr conf.ldif
}

function create_group(){

  # Create LDAP group

  local GROUP="$1"
  local USER_NAME="$2"

  if [[ -z "$GROUP" || -z "$USER_NAME" ]]; then
    usage
  fi

  read -r -d '' CONF <<EOF
dn: cn=$GROUP,ou=Groups,$BASE_DN
objectClass: top
objectClass: groupOfNames
cn: $GROUP
member: cn=$USER_NAME,ou=People,$BASE_DN
EOF
  echo "$CONF" > conf.ldif
  ldapadd \
    -v \
    -x \
    -H "ldaps://$LDAP_HOSTNAME" \
    -D "cn=$ADMIN_USER,$BASE_DN" \
    -w "$ADMIN_PASS" \
    -f conf.ldif
  rm -fr conf.ldif

}


function add_user_to_group(){

  # Add LDAP user to LDAP group

  local GROUP="$1"
  local USER_NAME="$2"

  if [[ -z "$GROUP" || -z "$USER_NAME" ]]; then
    usage
  fi

  read -r -d '' CONF <<EOF
dn: cn=$GROUP,ou=Groups,$BASE_DN
changetype: modify
add: member
member: cn=$USER_NAME,ou=People,$BASE_DN
EOF
  echo "$CONF" > conf.ldif

  ldapmodify \
    -v \
    -x \
    -H "ldaps://$LDAP_HOSTNAME" \
    -D "cn=$ADMIN_USER,$BASE_DN" \
    -w "$ADMIN_PASS" \
    -f conf.ldif
  rm -fr conf.ldif

}

function remove_user_from_group(){

  # Remove LDAP user from LDAP group

  local GROUP="$1"
  local USER_NAME="$2"

  if [[ -z "$GROUP" || -z "$USER_NAME" ]]; then
    usage
  fi

  read -r -d '' CONF <<EOF
dn: cn=$GROUP,ou=Groups,$BASE_DN
changetype: modify
delete: member
member: cn=$USER_NAME,ou=People,$BASE_DN
EOF
  echo "$CONF" > conf.ldif

  ldapmodify \
    -v \
    -x \
    -H "ldaps://$LDAP_HOSTNAME" \
    -D "cn=$ADMIN_USER,$BASE_DN" \
    -w "$ADMIN_PASS" \
    -f conf.ldif
  rm -fr conf.ldif

}

function delete_object(){

  # Delete LDAP object

  local DN="$1"
  if [[ -z "$DN" ]]; then
    usage
  fi

  ldapdelete \
    -v \
    -x \
    -H "ldaps://$LDAP_HOSTNAME" \
    -D "cn=$ADMIN_USER,$BASE_DN" \
    -w "$ADMIN_PASS" \
    "$DN"

}

function delete_user(){

  # Delete LDAP user

  local USER_NAME="$1"
  local USER_DN="cn=$USER_NAME,ou=People,$BASE_DN"
  if [[ -z "$USER_NAME" ]]; then
    usage
  fi
  delete_object "$USER_DN"
}

function delete_group(){

  # Delete LDAP group

  local GROUP_NAME="$1"
  local GROUP_DN="cn=$GROUP_NAME,ou=Groups,$BASE_DN"
  delete_object "$GROUP_DN"
}

parse_args "$@"

if [[ "$1" == '--help' || "$1" == 'help' ]]; then
  usage
fi

install_deps
init_ldap_info

if [[ "$ACTION" == "create_user" ]]; then
  create_user "$USER_NAME" "$PASSWORD" "$EMAIL" "$PHONE"
elif [[ "$ACTION" == "replace_attribute" ]]; then
  modify_user "replace" "$USER_NAME" "$ATTRIBUTE" "$VALUE"
elif [[ "$ACTION" == "add_attribute" ]]; then
  modify_user "add" "$USER_NAME" "$ATTRIBUTE" "$VALUE"
elif [[ "$ACTION" == "create_group" ]]; then
  create_group "$GROUP" "$USER_NAME"
elif [[ "$ACTION" == "add_user_to_group" ]]; then
  add_user_to_group "$GROUP" "$USER_NAME"
elif [[ "$ACTION" == "remove_user_from_group" ]]; then
  remove_user_from_group "$GROUP" "$USER_NAME"
elif [[ "$ACTION" == "delete_user" ]]; then
  delete_user "$USER_NAME"
elif [[ "$ACTION" == "delete_group" ]]; then
 delete_group "$GROUP"
elif [[ "$ACTION" == "delete_object" ]]; then
  delete_object "$DN"
elif [[ "$ACTION" == "show_directory" ]]; then
  show_directory
else
  usage
fi
