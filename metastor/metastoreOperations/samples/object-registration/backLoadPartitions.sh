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
# This script is used to submit Back Loading Partition request
# Usage: backLoadPartitions.sh <notification_name>
#--------------------------------------------------------------------------------------------
# Checking if required number of arguments are passed
if [ $# -lt 3 ]
  then
    echo "-------------------------------------------------------------------------------------------------"
    echo "Usage: $0 <notification_name> <delay_in_sec> <herd credentials>								   "
    echo "Invalid number of arguments supplied.. Exiting..                                                 "
    echo "-------------------------------------------------------------------------------------------------"
    exit 1
fi

home=`env | grep "^HOME=" | awk 'BEGIN {FS="="} {print $2}'`
MAIL_DL="{{EMAIL_LIST_OPS}}"

# Input Args
LIST_OF_NOTIFICATION_NAMES=$1
DELAY_IN_SECS_FOR_DM_CALL=$2
HERD_CREDENTIAL=$3

CLUSTER_NAME='metastore'
EXECUTION_ID='backLoadPartitions'
PARTITION_KEY=""
timestamp=$(date "+%Y%m%d%H%M%S")

SINGLETON_WF="singletonObjectsAddPartitionWorkflow"
DM_MANAGED_WF="addPartitionWorkflow"
section_delimiter="\n##############################################\n"

OUTPUT_DIR="/tmp"
NTFCN_REPONSE_FILE="${OUTPUT_DIR}/${NOTIFICATION_NAME}_$timestamp.xml"
rm -f $NTFCN_REPONSE_FILE
FORMAT_REPONSE_FILE="${OUTPUT_DIR}/${NOTIFICATION_NAME}_FORMAT_$timestamp.xml"
rm -f $FORMAT_REPONSE_FILE

LOG_FILE="${OUTPUT_DIR}/${NOTIFICATION_NAME}_back_load_partition_$timestamp.log"
rm -f $LOG_FILE

echo -e "${section_delimiter}\nBackload partitions script called with: \nNotificationName(s): ${LIST_OF_NOTIFICATION_NAMES}\nDelay: ${DELAY_IN_SECS_FOR_DM_CALL}\nPartition Values: ${PARAM_PARTITION_VALUE}"  | tee -a $LOG_FILE

echo -e "${section_delimiter}\nLoading service account credentials:" | tee -a $LOG_FILE

SCRIPT_PATH="$( cd "$(dirname "$0")" ; pwd -P )"
echo "BackLoad Script Path: $SCRIPT_PATH"

#Read https credentials file. This is needed to call Herd using curl
CREDENTIALS=`cat $HERD_CREDENTIAL`
if [[ -z $CREDENTIALS ]]
then
	echo "Could not get Service Account Credentials, EXITING!!!"
	exit 1
else
	echo "Service account credentials loaded successfully!!!" | tee -a $LOG_FILE
fi

#-----------------------------------------------------------------------------------------------------------------#
#                                                  SUBROUTINES                                                    #
#-----------------------------------------------------------------------------------------------------------------#

# proc for checking the succesful execution
function handle_error(){
   EXIT_CD=$1
   ERROR_MSG=$2
   if [ $EXIT_CD -ne 0 ]
   then
       echo -e "Error : $EXIT_CD -- $ERROR_MSG" | tee -a $LOG_FILE
       echo -e "Sending Email to $MAIL_DL" | tee -a $LOG_FILE
       EMAIL_SUBJECT="[ERROR] - FAILED to submit Back Load Partition job for $NOTIFICATION_NAME"
       if [ ! -z $MAIL_DL ]
       		then
                  MAIL_CMD="cat $LOG_FILE | mail -s \"${EMAIL_SUBJECT}\" \"$MAIL_DL\""
                  eval ${MAIL_CMD}
                 fi
       #exit 1
   else
     return
   fi
}

function get_partition_key(){
	NAMESPACE=$1
	OBJECT_NAME=$2
	USAGE=$3
	FILE_TYPE=$4
	JOB_NAME=$5

	echo -e "\ncurl --insecure -H "Authorization: Basic XXXXXXX" -H "Accept: application/xml" -H "Content-Type: application/xml" -X GET {{DM_REST_URL}}/businessObjectFormats/namespaces/$NAMESPACE/businessObjectDefinitionNames/$OBJECT_NAME/businessObjectFormatUsages/$USAGE/businessObjectFormatFileTypes/$FILE_TYPE" | tee -a $LOG_FILE

	curl --insecure -H "Authorization: Basic $CREDENTIALS" -H "Accept: application/xml" \
	-H "Content-Type: application/xml" -X GET \
	 {{DM_REST_URL}}/businessObjectFormats/namespaces/$NAMESPACE/businessObjectDefinitionNames/$OBJECT_NAME/businessObjectFormatUsages/$USAGE/businessObjectFormatFileTypes/$FILE_TYPE -s > $FORMAT_REPONSE_FILE
	 handle_error $? "Error while getting format information from Herd"

	echo -e "\nHerd GET Business Object Format Response" | tee -a $LOG_FILE
	cat $FORMAT_REPONSE_FILE | tee -a $LOG_FILE

	sed -i -e 's/version="1.1"/version="1.0"/' $FORMAT_REPONSE_FILE

	STATUS_CD=`xmllint --xpath '//errorInformation/statusCode/text()' $FORMAT_REPONSE_FILE`
	echo -e "\nHerd object format request status : $STATUS_CD\n" | tee -a $LOG_FILE

	if [[ ! -z $STATUS_CD ]]
	then
		if [ $STATUS_CD -ne "200" ]
		then
		   ERROR_MESSAGE=`xmllint --xpath '//errorInformation/message/text()' $FORMAT_REPONSE_FILE`
		   echo $ERROR_MESSAGE
		   handle_error 1 "$ERROR_MESSAGE"
		fi
	fi

	echo -e "xmllint --xpath '//partitionKey/text()' $FORMAT_REPONSE_FILE" | tee -a $LOG_FILE
	PARTITION_KEY=`xmllint --xpath '//partitionKey/text()' $FORMAT_REPONSE_FILE`
	echo -e "partition key is $PARTITION_KEY" | tee -a $LOG_FILE

	#for non-partitioned singleton table, set PARTITION_COLUMNS="none"
	if [[ -z $PARTITION_KEY ]]
	then
		 #for non-partitioned singleton table, set PARTITION_COLUMNS="none"
      if [ $JOB_NAME = "singletonObjectsAddPartitionWorkflow" ]
      then
         PARTITION_KEY="none"
      else
         echo -e "Partition Key is empty!!" | tee -a $LOG_FILE
      fi
	fi
}

function insert_notification(){
	sql="insert into DM_NOTIFICATION (NAMESPACE, OBJECT_DEF_NAME, USAGE_CODE, FILE_TYPE, WF_TYPE, EXECUTION_ID, CLUSTER_NAME, PARTITION_KEY, PARTITION_VALUES, CORRELATION_DATA)"
	sql+=" VALUES ('${NAMESPACE}','${OBJECT_NAME}','${USAGE}','${FILE_TYPE}','${WF_TYPE}','${EXECUTION_ID}','${CLUSTER_NAME}', '${PARTITION_KEY}', '${PARTITION_VALUE}', '${CORRELATION_DATA}');"

	echo -e "\nSQL to run: \n$sql"

	mysql -h {{RDS_HOST}} -P 3306 -u MS_Hive_0_13 --password='{{MS_HIVE_0_13_PWD}}' --ssl-ca=/etc/aws-rds/ssl/rds-combined-ca-bundle.pem metastor -e "$sql"

	handle_error $? "Could not add back load parition request in Herd Notification"
}

function reset_db_insert_values(){
	NAMESPACE=""
	OBJECT_NAME=""
	USAGE=""
	FILE_TYPE=""
	WF_TYPE=8
	PARTITION_KEY=""
	PARTITION_VALUE=""
	CORRELATION_DATA=""
}

function submit_notification_for_processing(){
	NOTIFICATION_NAME=$1

	# Reset to default values
	reset_db_insert_values

	echo -e "curl --insecure -H "Authorization: Basic XXXXXXX" -H "Accept: application/xml" -H "Content-Type: application/xml" -X GET {{DM_REST_URL}}/notificationRegistrations/businessObjectDataNotificationRegistrations/namespaces/METASTOR/notificationNames/$NOTIFICATION_NAME" | tee -a $LOG_FILE

	curl --insecure -H "Authorization: Basic $CREDENTIALS" -H "Accept: application/xml" \
	-H "Content-Type: application/xml" -X GET \
	 {{DM_REST_URL}}/notificationRegistrations/businessObjectDataNotificationRegistrations/namespaces/METASTOR/notificationNames/$NOTIFICATION_NAME -s \
	 > $NTFCN_REPONSE_FILE
	handle_error $? "Error while getting notification details from Herd"

	echo -e "\nHerd Response" | tee -a $LOG_FILE
	cat $NTFCN_REPONSE_FILE | tee -a $LOG_FILE

	# xmllint doesn't parse xml 1.1 so we need this hack.
	sed -i -e 's/version="1.1"/version="1.0"/' $NTFCN_REPONSE_FILE

	STATUS_CD=`xmllint --xpath '//errorInformation/statusCode/text()' $NTFCN_REPONSE_FILE`
	echo -e "\nHerd notification request status : $STATUS_CD\n" | tee -a $LOG_FILE

	if [[ ! -z $STATUS_CD ]]
	then
		if [ $STATUS_CD -ne "200" ]
		then
		   ERROR_MESSAGE=`xmllint --xpath '//errorInformation/message/text()' $NTFCN_REPONSE_FILE`
		   echo $ERROR_MESSAGE
		   handle_error 1 "$ERROR_MESSAGE"
		fi
	fi

	JOB_NAME=`xmllint --xpath '//jobAction/jobName/text()' $NTFCN_REPONSE_FILE`
	echo -e "Job Name : $JOB_NAME " | tee -a $LOG_FILE

	NAMESPACE=`xmllint --xpath '//businessObjectDataNotificationFilter/namespace/text()' $NTFCN_REPONSE_FILE`
	echo -e "Namespace : $NAMESPACE" | tee -a $LOG_FILE

	OBJECT_NAME=`xmllint --xpath '//businessObjectDataNotificationFilter/businessObjectDefinitionName/text()' $NTFCN_REPONSE_FILE`
	echo -e "Object Name : $OBJECT_NAME" | tee -a $LOG_FILE

	USAGE=`xmllint --xpath '//businessObjectDataNotificationFilter/businessObjectFormatUsage/text()' $NTFCN_REPONSE_FILE`
	echo -e "Format Usage : $USAGE" | tee -a $LOG_FILE

	FILE_TYPE=`xmllint --xpath '//businessObjectDataNotificationFilter/businessObjectFormatFileType/text()' $NTFCN_REPONSE_FILE`
	echo -e "File Type : $FILE_TYPE" | tee -a $LOG_FILE

	CORRELATION_DATA=`xmllint --xpath '//jobAction/correlationData/text()' $NTFCN_REPONSE_FILE`
	echo -e "Correlation Data : $CORRELATION_DATA " | tee -a $LOG_FILE
	CORRELATION_DATA="${CORRELATION_DATA/<![CDATA[/}"
	CORRELATION_DATA="${CORRELATION_DATA/]]>/}"

	if [ $JOB_NAME = $SINGLETON_WF ]
	then

		WF_TYPE=1
		get_partition_key $NAMESPACE $OBJECT_NAME $USAGE $FILE_TYPE $JOB_NAME
		PARTITION_VALUE=$(date "+%Y-%m-%d")
	elif [[ -n ${PARAM_PARTITION_VALUE} ]] && [ $JOB_NAME =  $DM_MANAGED_WF ]
	then

		WF_TYPE=0
		get_partition_key $NAMESPACE $OBJECT_NAME $USAGE $FILE_TYPE $JOB_NAME
		PARTITION_VALUE=${PARAM_PARTITION_VALUE}
	elif [ $JOB_NAME != $DM_MANAGED_WF ]
	then
		echo -e "\nWARNING: WORKFLOW TYPE EXCLUDED FROM BACK LOADING FOUND: Notificaiton Name:${NOTIFICATION_NAME}, Job Name: ${JOB_NAME}"
		return
	fi

	insert_notification
}

#-----------------------------------------------------------------------------------------------------------------#
#                                                  MAIN SECTION                                                   #
#-----------------------------------------------------------------------------------------------------------------#

for notificationName in $(echo $LIST_OF_NOTIFICATION_NAMES | sed 's/,/ /g')
do
	echo -e "${section_delimiter}Processing Notification: ${notificationName}\n" | tee -a $LOG_FILE
	submit_notification_for_processing $notificationName
	echo -e "\nWaiting for $DELAY_IN_SECS_FOR_DM_CALL sec to make another Herd Call..."
	sleep $DELAY_IN_SECS_FOR_DM_CALL
done