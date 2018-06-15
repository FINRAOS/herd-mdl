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

mdlInstanceName=$1
environment=$2
stagingBucket=$3
javaKeyStoreFile=$4
regionName=$5

export STAGING_BUCKET=${stagingBucket}
export S3_LOCATION="s3://$STAGING_BUCKET/deploy/bdsql/bootstrap/"
echo "started download BDSQL artifacts. S3_location=$S3_LOCATION"
sudo aws s3 sync "$S3_LOCATION" /opt/mdl/bdsql/ #--include '*.sh'

function log(){

  # Log output to screen
  # input: log message

  local MSG="$1"

  local LOG_SOURCE=$(basename $0 2> /dev/null || echo "logger")
  echo "$(date '+%F %T') bdsql-$LOG_SOURCE[$$]: $MSG"
}

function prestoBootstrapHelper() {

  IS_MASTER=$(grep isMaster /mnt/var/lib/info/instance.json | awk -F ':' '{print $2}' | sed -e 's/ //g')

  CLUSTER_ID=$(aws ec2 describe-tags --filters "Name=resource-id,Values=$(curl -s http://169.254.169.254/latest/meta-data/instance-id)" "Name=key,Values=aws:elasticmapreduce:job-flow-id"  | jq ".Tags[0].Value" | sed -e 's/"//g')
  APP_VERSIONS=$(aws emr describe-cluster --cluster-id "$CLUSTER_ID" | jq '.Cluster.Applications[] | "\(.Name)=\(.Version)"' | sed -e 's/"//g' | sort | tr "\n" " " | tr "[:upper:]" "[:lower:]" | sed -e 's/ $/\n/g')
  EMR_VERSION=$(aws emr describe-cluster --cluster-id "$CLUSTER_ID" | jq '.Cluster.ReleaseLabel' | sed -e 's/"//g' -e 's/emr-//g')

  LDAP_SERVER=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/HostName --output text --query Parameter.Value)
  LDAP_BASE_DN=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/LDAP/BaseDN --output text --query Parameter.Value)

  log "starting Presto bootstrap: IS_MASTER=$IS_MASTER EMR_VERSION=$EMR_VERSION APP_VERSIONS=$APP_VERSIONS LDAP_SERVER=$LDAP_SERVER LDAP_BASE_DN=$LDAP_BASE_DN"

  if [[ "$IS_MASTER" = true ]]; then
    # Get hive password
    hiveAccountPassword=$(aws ssm get-parameter --name /app/MDL/${mdlInstanceName}/${environment}/METASTOR/HIVE/hiveAccount --with-decryption --region ${regionName} --output text --query Parameter.Value)
    echo "Waiting for hive-site.xml"
    while [[ `cat /etc/hive/conf/hive-site.xml 2>/dev/null | grep {{HIVE_PASSWORD}} | wc -l` = "0" ]]; do echo "."; sleep 1; done

    # Update the password
    sudo sed -i "s/{{HIVE_PASSWORD}}/${hiveAccountPassword}/g" /etc/hive/conf/hive-site.xml

    # Restart the services
    sudo stop hive-server2
    sudo stop hive-hcatalog-server
    sudo start hive-server2
    sudo start hive-hcatalog-server
  fi

  log "started updating ulimits"
  read -r -d '' FILE_DESCRIPTORS <<EOF
presto  soft    nofile  32768
presto  hard    nofile  65535
EOF
  sudo echo "$FILE_DESCRIPTORS" > /tmp/presto_ulimits.conf
  sudo cp /tmp/presto_ulimits.conf /etc/security/limits.d/presto.conf
  log "finished updating ulimits"

  log "started waiting for EMR presto app"
  # wait for presto-server.pid to appear
  while [[ ! -f /var/run/presto/presto-server.pid ]]; do sleep 10; done
  log "finished waiting for EMR presto app"

  log "started updating presto keystore"
  sudo aws s3 cp ${javaKeyStoreFile} /etc/presto/mdl.jks
  sudo chmod 644 /etc/presto/mdl.jks
  log "finished updating presto keystore"

  if [[ "$IS_MASTER" = true ]]; then

    log "started getting LDAP cert"

    # export LDAP server cert
    echo | openssl s_client -connect "$LDAP_SERVER:636" | sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > ldapserver.pem
    # convert LDAP server cert from pem to der
    openssl x509 -outform der -in ldapserver.pem -out ldapserver.der
    # import LDAP server cert into Java truststore
    sudo keytool -import -alias mdl-ldap --keystore /etc/pki/ca-trust/extracted/java/cacerts --storepass changeit -file ldapserver.der -noprompt
    # remove temporary LDAP certs
    rm -fr ldapserver.pem ldapserver.der

    log "finished getting LDAP cert"
  fi

  log "started updating Presto config"

  sudo cp -pr /etc/presto/conf.dist /tmp/presto_conf.dist.bak

  while [[ ! -f /etc/presto/conf/jvm.config ]]; do sleep 5; done
  sudo bash -c 'echo "-DHADOOP_USER_NAME=hive" >> /etc/presto/conf/jvm.config'
  sudo bash -c 'echo "-Dlog.levels-file=/etc/presto/conf/log.properties" >> /etc/presto/conf/jvm.config'
  #sudo bash -c 'echo "-agentpath:/home/hadoop/libjvmkill.so" >> /etc/presto/conf/jvm.config'
  sudo sed -i -e 's/-XX:+UseConcMarkSweepGC/\-XX:\+UseG1GC\n\-XX:G1HeapRegionSize=32M\n\-XX:\+UseGCOverheadLimit/g' /etc/presto/conf/jvm.config

  while [[ ! -f /etc/presto/conf/log.properties ]]; do sleep 5; done
  sudo bash -c 'echo "com.facebook.presto.server.security.LdapFilter=DEBUG" >> /etc/presto/conf/log.properties'

  while [[ ! -f /etc/presto/conf/config.properties ]]; do sleep 5; done
  sudo bash -c "sed -i -e '/^http-server.https.keymanager.password*/d' /etc/presto/conf/config.properties"

  # disable legacy params if SQL authorization is enabled
  while [[ ! -f /etc/presto/conf/catalog/hive.properties ]]; do sleep 5; done
  sudo bash -c "sed -i -e '/^hive\.allow.*\(table\|column\)/d' /etc/presto/conf/catalog/hive.properties"

  if [[ "$EMR_VERSION" == "5.13.0" ]]; then
      # unsupported settings in EMR 5.13.0
      sudo bash -c "sed -i -e '/^hive\.s3\.\(socket-timeout\|sse\.enabled\|staging-directory\)/g' /etc/presto/conf/catalog/hive.properties"
  fi

  DISCOVERY_PORT=$(grep http-server.http.port /etc/presto/conf/config.properties | awk -F '=' '{print $2}')
  COORDINATOR=$(grep discovery.uri /etc/presto/conf/config.properties | awk -F '=' '{print $2}' | egrep -o '//[^:]+:' | sed -e 's/://g' -e 's/\///g')
  sudo bash -c "sed -i -e 's#^discovery.uri.*#discovery.uri = http://$COORDINATOR:$DISCOVERY_PORT#g' /etc/presto/conf/config.properties"

  log "finished updating Presto config"

  if [[ "$IS_MASTER" = true ]]; then
    log "started updating DNS record"
    DNS_ALIAS=$(echo "$LDAP_BASE_DN" | sed -e 's/dc=//g' -e 's/,/./g' -e 's/^/bdsql./g')
    echo -e "127.0.0.1\t$DNS_ALIAS" | sudo tee -a /etc/hosts > /dev/null
    log "finished updating DNS record"

  else
    log "started disabling LDAP on worker node"
    sudo bash -c "sed -i -e '/^http-server.authentication.type*/d' /etc/presto/conf/config.properties"
    sudo bash -c "sed -i -e '/^authentication.ldap.url*/d' /etc/presto/conf/config.properties"
    sudo bash -c "sed -i -e '/^authentication.ldap.user-bind-pattern*/d' /etc/presto/conf/config.properties"
    log "finished disabling LDAP on worker node"
  fi

  log "finished Presto bootstrap"

}

function excuteBackgroundActions(){

  echo "installing deps"
  sudo yum install gcc openldap-clients openldap-devel -y
  sudo pip install awscli boto3 MySQL-python python-ldap pyhs2 --upgrade

  # Update client
  execute_cmd "sudo yum -y install aws-cli"
  execute_cmd "aws configure set default.region ${regionName}"

  echo "configuring AWS RDS SSL CA certificate"
  sudo mkdir -p /etc/hive/conf/
  sudo wget "https://s3.amazonaws.com/rds-downloads/rds-combined-ca-bundle.pem" -O "/etc/hive/conf/rds-combined-ca-bundle.pem"

  echo "executing action_update_presto_config.sh"
  prestoBootstrapHelper

  echo "restarting presto-server after all actions are done"
  sudo stop presto-server
  sudo start presto-server

}

echo "started bootstrap background function"
excuteBackgroundActions &
echo "finished bootstrap background function"
