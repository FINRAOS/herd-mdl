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
#--------------------------------------------------------------------------------------------
# This script is used to launch clusters to prevent jobs being starved due to cluster failure.
#
# Usage: cluster-launcher.sh
#--------------------------------------------------------------------------------------------

# Checking if required number of arguments are passed
if [ $# -lt 6 ]
  then
    echo "-------------------------------------------------------------------------------------------------"
    echo "Usage: $0 <SDLC> <BASE_DIR> <CRED_FILE> <MAIL_DL> <CLUSTER_NAME> <CLUSTER_DEF>				   "
    echo "Invalid number of arguments supplied.. Exiting..                                                 "
    echo "-------------------------------------------------------------------------------------------------"
    exit 1
fi

# Input Params
SDLC=$1
BASE_DIR=$2
HERD_CRED=$3
MAIL_DL=$4
cluster_name=$5
cluster_def=$6

timestamp=$(date "+%Y%m%d%H%M%S")
emr_create_cluster_template="${BASE_DIR}/emrClusterRequest.xml"
LOG_DIR="/tmp"
LOG_FILE="${LOG_DIR}/cluster_launcher_$timestamp.log"

rm -f $LOG_FILE

# Displaying input parameters
echo "Running $0 with:"
echo -e "SDLC=${SDLC} \n Base Dir=${BASE_DIR} \n CRED File=${HERD_CRED} \n Mail DL=${MAIL_DL}"


function handle_error(){
   EXIT_CD=$1
   ERROR_MSG=$2
   if [ $EXIT_CD -ne 0 ]
     then
       echo -e "Cluster Launcher Job Failed" | tee -a $LOG_FILE
       echo -e "Error : $ERROR_MSG" | tee -a $LOG_FILE
	   echo -e "Sending Email to $MAIL_DL" | tee -a $LOG_FILE
       EMAIL_SUBJECT="[ERROR]-[$SDLC] - METASTOR Cluster Launcher Failed"
       if [ ! -z $MAIL_DL ]
		then
               MAIL_CMD="cat $LOG_FILE | mail -s \"${EMAIL_SUBJECT}\" \"$MAIL_DL\""
               eval ${MAIL_CMD}
        fi

       exit 1
   else
     return
   fi
}

CREDENTIALS=`cat $HERD_CRED`
if [[ -z $CREDENTIALS ]]
then
   handle_error 1 "Could not read Herd Credentials from ${HERD_CRED}"
fi

cluster_post=/${LOG_DIR}/${cluster_name}_cluster_$timestamp.xml
CLUSTER_RESPONSE_FILE=/${LOG_DIR}/${cluster_name}_response_$timestamp.xml

cp -f $emr_create_cluster_template $cluster_post    >> $LOG_FILE 2>&1

sed -i "s/__CLUSTER_DEF__/${cluster_def}/g" $cluster_post >> $LOG_FILE 2>&1
sed -i "s/__CLUSTER_NAME__/${cluster_name}/g" $cluster_post >> $LOG_FILE 2>&1

echo -e "Curl request to create a cluster" 2>&1 | tee -a $LOG_FILE
curl --insecure -H "Authorization: Basic $CREDENTIALS" -H "Accept: application/xml" \
-H "Content-Type: application/xml" -X POST -d @$cluster_post {{DM_REST_URL}}/emrClusters -s > $CLUSTER_RESPONSE_FILE

handle_error $? "Error creating cluster"
cluster=`cat $CLUSTER_RESPONSE_FILE`
CLUSTER_ID=`expr "$cluster" : '.*<id>\(.*\)</id>'`
CLUSTER_CREATED=`expr "$cluster" : '.*<emrClusterCreated>\(.*\)</emrClusterCreated>'`
echo "Cluster ID is $CLUSTER_ID" 2>&1 | tee -a $LOG_FILE


