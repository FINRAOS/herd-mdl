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
#------------------------------------------------------------------------------------------------------------------#
# FILENAME      : registerBusinessObjects.sh                                                                       #
# DESCRIPTION   : This shell script is used to parse a pipe delimited text file and generate post and put xml      #
#                 files from their templates for each line in the file. Once generated, curl commands will be run  #
#                 to update, drop or add object registrations.                                                     #
#                                                                                                                  #
# NOTES:                                                                                                           #
#    POST   - Creates a new business object data notification registration.                                        #
#    PUT    - Updates an existing business object data notification registration status.                           #
#    DELETE - Drops an existing business object data notification.                                                 #
#------------------------------------------------------------------------------------------------------------------#

# Checking if required number of arguments are passed
if [ $# -lt 1 ]
  then
    echo "-------------------------------------------------------------------------------------------------"
    echo "Usage: $0 <OBJ_REG_REQEUST>"
    echo "Invalid number of arguments supplied.. Exiting..                                                 "
    echo "-------------------------------------------------------------------------------------------------"
    exit 1
fi

JIRA_ISSUE=$1
HERD_REST_URL="{{DM_REST_URL}}"
HERD_CREDENTIAL={{DMPASS_BASE64_CRED_PATH}}

# Constants required to set before running the script
CLUSTER_DEF="{{CLUSTER_DEF_NAME}}"
CLUSTER_NAME="registerBusinessObjects"
RDS_HOST="{{RDS_HOST}}"
DB_USER="{{MS_HIVE_0_13_USER}}"
DB_PWD="{{{MS_HIVE_0_13_PWD}}}"

MDL_METASTOR_NAMESPACE="{{MDL_METASTOR_NAMESPACE}}"

delim="##### WARNING #####"
warning_count=0
warnings_detected=0
errors_detected=0
line_count=0

host=`hostname`
date=`date +%Y%m%d%H%M%S`
user=`whoami`
python=`which python`

text_file="objectRegistrationRequests/${JIRA_ISSUE}.txt"
text_file_stripped="/tmp/object_registration_stripped.txt"

post_template="post_bo_data_notification_registration_template.xml"
storage_post_template="post_bo_storage_unit_registration_template.xml"
post_file="/tmp/post_bo_data_notification_registration.xml"
put_template="put_bo_data_notification_registration_template.xml"
storage_put_template="put_bo_storage_unit_registration_template.xml"
put_file="/tmp/put_bo_data_notification_registration.xml"

back_load_notifications_arr=()
# this will store the status of each object if
back_load_status_by_line=""


#-----------------------------------------------------------------------------------------------------------------#
#                                                  SUBROUTINES                                                    #
#-----------------------------------------------------------------------------------------------------------------#

function check_for_bad_chars() {

    # This subroutine checks the input string to see if there are any illegal chars present.
    # This is different for lines and notification names, hence the mode param.

    # The ^ symbol in the regex says to match on anything other than the allowed chars

    mode=$1
    string=$2

    retval=0

    if [ "$mode" = "notification_name" ]
    then
        # For notification names, only the following are allowed:
        # Letters (lower and upper) a-zA-Z
        # Numbers 0-9
        # Underscores "_"

        if [[ "$string" =~ [^a-zA-Z0-9_] ]]
        then
            retval=1
        fi
    else
        # Add the following additional characters for processing the initial line:
        # Pipe symbols |
        # Hypens, periods and spaces.

        if [[ "$string" =~ [^a-zA-Z0-9|_-.\ ] ]]
        then
            retval=1
        fi
    fi

    return "$retval"
}

function add_json_to_file() {

    # This subroutine will read the specified file and add the current json text to the file.
    file=$1
    new_file="${file}_new"

    # Remove any previous version of the "new" file to make certain that we are starting fresh.
    if [ -f "$new_file" ]
    then
        rm $new_file
    fi

    IFS=''
    while read j_line
    do
        if [[ $j_line =~ .*__JSON__.* ]]
        then
            if [ "X$JSON_FILE" != "X" ]
            then
                # A json file was provided, add the JSON_TEXT to the new file in place of the __JSON__ line.
                # Otherwise, don't do anything as we don't want a blank line to be present in the new file.
                echo "$JSON_TEXT" >> $new_file
            fi
        else
            echo "$j_line" >> $new_file
        fi
    done < $file

    # Now move the new file back over the original file so it can be used.
    mv $new_file $file
}

function clean_logfile() {

    tr=`which tr`

    dos2unix $logfile >/dev/null 2>&1

    # Run tr command against the logfile output in case the aws s3 command that is run results in ^M characters
    # appearing in the log which causes mail to fail.

    $tr -d '\015' < ${logfile} > ${logfile}_clean
    mv ${logfile}_clean $logfile
}

function join { local IFS="$1"; shift; echo "$*"; }

function submit_notifications_for_backloading(){
	echo -e "\n##############################################\n\n$MDL_METASTOR_NAMESPACE object registration script back load partitions status.\n" >> $back_load_partitions_status_file

	# Output the list of lines with status to a temp file and then append the logfile to this file. Doing so will result
	# in an email that shows the lines with warnings at the top of the email rather than at the end making it easier to read.
	echo -e "Back load partitions status for the following lines in the object_registration.txt file:" >> $back_load_partitions_status_file
	echo -e "$back_load_status_by_line" >> $back_load_partitions_status_file
	echo -e "\n##############################################" >> $back_load_partitions_status_file
	# Append the logfile to the status file
	cat $back_load_partitions_log_file >> $back_load_partitions_status_file

	# Move the updated warnings file back over the logfile
	mv $back_load_partitions_status_file $back_load_partitions_log_file

	if [ -n "$back_load_notifications_arr" ]; then
		echo -e "\nSENDING FOLLOWING NOTIFICATION(S) TO BACKLOAD PARTITIONS: ${back_load_notifications_arr[@]}" >> $back_load_partitions_log_file
		sh ./backLoadPartitions.sh $(join , ${back_load_notifications_arr[@]}) 5 $HERD_CREDENTIAL >> $back_load_partitions_log_file
	else
		echo -e "\nNO NOTIFICATION(S) FOUND TO BACKLOAD PARTITIONS!!!" >> $back_load_partitions_log_file
	fi
}

function passemail() {

    # Run clean_logfile against logfile to remove any ^M symbols that exist in the log to enable email to work.
    clean_logfile

    if [ $warnings_detected -gt 0 ]
    then
        echo -e "\n##############################################\n\n$MDL_METASTOR_NAMESPACE object registration script completed with detected errors.\n" >> $logfile

        # Output the list of lines with warnings to a temp file and then append the logfile to this file. Doing so will result
        # in an email that shows the lines with warnings at the top of the email rather than at the end making it easier to read.
        echo -e "Warnings/errors were detected in the following lines in the object_registration.txt file:" >> $warnings_file
        echo -e "$list_of_warnings_by_line" >> $warnings_file
        echo -e "\n##############################################" >> $warnings_file
        # Append the logfile to the warnings file
        cat $logfile >> $warnings_file

        # Move the updated warnings file back over the logfile
        mv $warnings_file $logfile

        submit_notifications_for_backloading

        if [ $errors_detected -gt 0 ]
        then
            leave 1
        else
            leave 99
        fi
    else
    	submit_notifications_for_backloading

        echo -e "\n##############################################\n"            >> $logfile
        echo -e "\n$MDL_METASTOR_NAMESPACE object registration script completed successfully.\n" >> $logfile
        leave 0
    fi
}

function leave() {
    status=${1:-101}

    if [ -n "$errstring" -o "$status" -ne 0 ] ; then

        # Call clean_logfile to remove any ^M symbols that may cause email to fail
        clean_logfile

        # Delete the previous all_obj_reg_warnings file
        rm  /tmp/all_obj_reg_warnings

        # Capture all of the errors to file and append the logfile to the end of the file
        for(( i=0; i<${#all_errors[@]}; i++ )); do
            echo ${all_errors[$i]} >> /tmp/all_obj_reg_warnings
        done

        echo -e "\n###########################\n" >> /tmp/all_obj_reg_warnings
        cat $logfile >> /tmp/all_obj_reg_warnings

        mv /tmp/all_obj_reg_warnings $logfile
    fi
    echo "Business ojbect registration deployment completed with status $status at `date -Iseconds`" >> $logfile
    trap - exit
    exit $status
} # leave()

trap leave hup int quit term exit

function errtrap() {
  errcode=$?
  set +xv
  failedcmd="$BASH_COMMAND"
  if [[ "$failedcmd" =~ "return \"\$retval\"" ]] || [[ "$failedcmd" =~ "json_tool_response" ]]
  then
      echo -e "\nTrapped error code contains retval or is related to a json tool detected error. We are not treating these as an error."
  else
      errmsg="$delim `date` \"$failedcmd\" exited non-zero status $errcode on line ${BASH_LINENO[0]}. $delim"
      all_errors+=("$errmsg")
      echo -e "\n$errmsg" >> $logfile
      set -xv
      warning_count=$((warning_count+1))
  fi
} # errtrap()

trap errtrap ERR

function parse_xml_response() {

    response_file=$1
    status_code=""
    retval=""
    ######################################################################################################################
    #
    #                      Logic behind dealing with 404 responses to curl commands
    #
    # A 404 response from the curl commands being run implies that the specified Business object data notification
    # registration with name "XXX" does not exist for the provided namespace.
    #
    # The following shows when 404 errors will be considered as errors, warnings or ignored:
    #
    # 1. Action = "add" and the curl command  = PUT named object         - treat as a WARNING as the specified object
    #                                                                      was not present as expected
    # 2. Action = "add" and the curl command  = DELETE named object      - ignore as the object may not exist
    # 3. Action = "add" and the curl command  = POST named object        - treat as a real error
    # 4. Action = "add" and the curl command  = DELETE named object      - treat as a real error as the temp object should
    #                                                                      exist due to the POST of the named temp object
    # 5. Action = "drop" and the curl command = DELETE named object      - treat as a WARNING as the specified object
    #                                                                      was not present as expected
    # 6. Action = "drop" and the curl command = DELETE temp named object - ignore as the temp object may not exist
    # 7. Action = "enable" and the curl command = POST named object      - treat as a WARNING as the specified object
    #                                                                      was not present as expected
    # 8. Action = "disable" and the curl command = POST named object     - treat as a WARNING as the specified object
    #                                                                      was not present as expected
    # 9. Action = "add" or "enable" or "disable" and the curl            - ignore as the object may not exist
    #    command = PUT named object
    #10. Action = "add" or "enable" or "disable" and the curl            - treat as a real error, business object format
    #    command = GET business object format                              should exist if trying to PUT or POST
    ######################################################################################################################

    if [[ -s $response_file ]]
    then
        echo -e "\nRespsone file $response_file contains content."
    else
        echo -e "\nRespsone file $response_file is empty." | tee -a $logfile
        warning_count=$((warning_count+1))
        errors_detected=$((errors_detected+1))
        retval=1
        return "$retval"
    fi

    buffer="${buffer} `echo -e "\nResponse file: $response_file"`"

    # xmllint doesn't parse xml 1.1 so we need this hack.
    sed -i -e 's/version="1.1"/version="1.0"/' $response_file

    status_code=`xmllint --xpath '//errorInformation/statusCode/text()' $response_file`

    # If no error occurs, then no status code is returned as the above xmllint command is looking specifically for the errorInformation statusCode
    if [[ ! -z $status_code ]]
    then
        # A status code was detected
        if [ $status_code -ne "200" ]
        then
            # Check to see if this is a 404 status code as we handle these differently depending on the action and curl command being run
            if [ $status_code -eq "404" ]
            then
                if [[ "$response_file" == *metastor_bo_get_response* ]] && [ "$ACTION" = "add" ]
                then
                    # Action = "add" and the curl command = GET named object
                    #        - treat as a WARNING as the specified object may not be present and we don't want to fail the deploy, we simply
                    #          want to mark this as a WARNING and keep going. This will allow a build to succeed in QA even if the object is
                    #          present in QA, but is present in PROD. If we don't do this, the pipeline will not be triggerable in PROD.
                    buffer="${buffer} `echo -e "\nStatus code from curl command: $status_code"`"
                    error_message=`xmllint --xpath '//errorInformation/message/text()' $response_file`
                    buffer="${buffer} `echo -e "\nWarning message: $error_message"`"
                    warning_count=$((warning_count+1))
                    retval=1
                elif [[ "$response_file" == *metastor_bo_delete_response* ]] && [ "$ACTION" = "add" ]
                then
                    # 2. Action = "add" and the curl command = DELETE named object - ignore as the object may not exist
                    echo -e "\nError code of 404 returned. This is an expected response for this DELETE curl command. No action necessary."
                    retval=0
                elif ([[ "$response_file" == *metastor_sr_delete_response* ]] || [[ "$response_file" == *metastor_sa_delete_response* ]])
                then
                    # These 2 were added later on, additional DELETE curl commands associated with the "drop" action. 404 responses may be ignored.
                    # 6. Action = "drop" and the curl command = DELETE temp named object - ignore as the temp object may not exist
                    echo -e "\nError code of 404 returned. This is an expected response for this DELETE curl command. No action necessary."
                    retval=0
                elif [[ "$response_file" == *metastor_bo_email_delete_response* ]]
                then
                    # 6. Action = "drop" and the curl command = DELETE temp named object - ignore as the temp object may not exist
                    echo -e "\nError code of 404 returned. This is an expected response for this DELETE curl command. No action necessary."
                    retval=0
                elif [[ "$response_file" == *metastor_bo_delete_response* ]] && [ "$ACTION" = "drop" ]
                then
                    # 5. Action = "drop" and the curl command = DELETE named object
                    #    - treat as a WARNING as the specified object was not present as expected
                    buffer="${buffer} `echo -e "\nStatus code from curl command: $status_code"`"
                    error_message=`xmllint --xpath '//errorInformation/message/text()' $response_file`
                    buffer="${buffer} `echo -e "\nWarning message: $error_message"`"
                    warning_count=$((warning_count+1))
                    retval=1
                elif ([[ "$response_file" == *metastor_bo_post_response* ]] || [[ "$response_file" == *metastor_storage_post_response* ]]) && ([ "$ACTION" = "enable" ] || [ "$ACTION" = "disable" ])
                then
                    # 7 & 8. Action = "enable" or "disable" and the curl command = POST named object
                    #        - treat as a WARNING as the specified object was not present as expected
                    buffer="${buffer} `echo -e "\nStatus code from curl command: $status_code"`"
                    error_message=`xmllint --xpath '//errorInformation/message/text()' $response_file`
                    buffer="${buffer} `echo -e "\nWarning message: $error_message"`"
                    warning_count=$((warning_count+1))
                    retval=1
                elif ([[ "$response_file" == *metastor_bo_put_response* ]] || [[ "$response_file" == *metastor_storage_put_response* ]]) && ([ "$ACTION" = "add" ] || [ "$ACTION" = "enable" ] || [ "$ACTION" = "disable" ])
                then
                    # 9. Action = "add" or "enable" or "disable" and the curl command = PUT named object
                    #    - return a value of 1 to check if there is an issue with the data given or if
                    #    the business object registration simply does not exist
                    echo -e "\nError code of 404 returned. This could be due to incorrect business object information or the business object may simply not existing."
                    retval=1
                elif ([[ "$response_file" == *metastor_su_delete_response* ]])
                then
                    # These 2 were added later on, additional DELETE curl commands associated with the "drop" action. 404 responses may be ignored.
                    # 6. Action = "drop" and the curl command = DELETE temp named object - ignore as the temp object may not exist
                    echo -e "\nError code of 404 returned. This is an expected response for this DELETE curl command. No action necessary."
                    retval=0
                else
                    # 1. Action = "add" and the curl command  = POST temp named object   - treat as a real error
                    # 3. Action = "add" and the curl command  = POST named object        - treat as a real error
                    # 4. Action = "add" and the curl command  = DELETE temp named object
                    #    - treat as a real error as the temp object should exist due to the POST of the named temp object
                    # 10. Action = "add" or "enable" or "disable" and the curl command = GET business object format
                    #    - treat as a real error, business object format should exist if trying to PUT or POST
                    buffer="${buffer} `echo -e "\nStatus code from curl command: $status_code"`"
                    error_message=`xmllint --xpath '//errorInformation/message/text()' $response_file`
                    buffer="${buffer} `echo -e "\nError message: $error_message"`"
                    warning_count=$((warning_count+1))
                    errors_detected=$((errors_detected+1))
                    retval=1
                fi
            else
                # This is a non-404 error. Mark it and move on.
                buffer="${buffer} `echo -e "\nStatus code from curl command: $status_code"`"
                error_message=`xmllint --xpath '//errorInformation/message/text()' $response_file`
                buffer="${buffer} `echo -e "\nError message: $error_message"`"
                warning_count=$((warning_count+1))
                errors_detected=$((errors_detected+1))
                retval=1
            fi
        else
            # Should never end up here, but just in case...
            echo -e "\nError code of 200 returned. This implies that no error occurred. No action necessary."
            retval=0
        fi
    elif [[ "$response_file" == *metastor_bo_get_response* ]] && ([ "$(grep -c "<schema>" $response_file)" -eq 0 ])
    then
        # A Business Object Format GET response will return no status code if successful, the response needs to be checked to see if the schema is present.
        echo -e "\nThe schema was not specified in the Business Object Format Registration"
        error_message="Schema information was not found for the Business Object Format Registration"
        warning_count=$((warning_count+1))

        retval=2
    else
        echo -e "\nNo error detected in response file. This implies that the curl command was successful."
        retval=0
    fi
    return "$retval"
}

# Register for Storage Unit Notification
function register_storage_unit_notification()
{
    echo -e "\nStorage Unit registration function called for $@." >> $logfile
    STORAGE_NOTIFICATION_NAME=$1
    NEW_STATUS=$2

    # put or post for storage notifications
    cp -f $storage_post_template $post_file     >> $logfile 2>&1
    cp -f $storage_put_template $put_file       >> $logfile 2>&1

    ##########

    # Run sed commands to exchange replacement parameters with values for the current business object.

    # POST FILE:
    sed -i "s/__NOTIFICATION_NAME__/$STORAGE_NOTIFICATION_NAME/g" $post_file     >> $logfile 2>&1
    sed -i "s/__NAMESPACE__/$NAMESPACE/g" $post_file                     >> $logfile 2>&1
    sed -i "s/__BO_FORMAT_FILE_TYPE__/$BO_FORMAT_FILE_TYPE/g" $post_file >> $logfile 2>&1
    sed -i "s/__BO_FORMAT_USAGE__/$BO_FORMAT_USAGE/g" $post_file         >> $logfile 2>&1
    sed -i "s/__BO_DEFINITION_NAME__/$BO_DEFINITION_NAME/g" $post_file   >> $logfile 2>&1
    sed -i "s/__WORKFLOW_TYPE__/$WF_TYPE/g" $post_file                   >> $logfile 2>&1
    sed -i "s/__NEW_STATUS__/$NEW_STATUS/g" $post_file                   >> $logfile 2>&1


    # PUT FILE:
    sed -i "s/__NAMESPACE__/$NAMESPACE/g" $put_file                      >> $logfile 2>&1
    sed -i "s/__BO_FORMAT_FILE_TYPE__/$BO_FORMAT_FILE_TYPE/g" $put_file  >> $logfile 2>&1
    sed -i "s/__BO_FORMAT_USAGE__/$BO_FORMAT_USAGE/g" $put_file          >> $logfile 2>&1
    sed -i "s/__BO_DEFINITION_NAME__/$BO_DEFINITION_NAME/g" $put_file    >> $logfile 2>&1
    sed -i "s/__WORKFLOW_TYPE__/$WF_TYPE/g" $put_file                    >> $logfile 2>&1
    sed -i "s/__NEW_STATUS__/$NEW_STATUS/g" $put_file                    >> $logfile 2>&1


    # Call add_json_to_file subroutine for the post_file and the put_file files
    add_json_to_file $post_file
    add_json_to_file $put_file


     # 1. PUT NOTIFICATION For STORAGE_ARCHIVE:

    buffer=`echo -e "----------\nPUT NOTIFICATION: Running curl get command to PUT storage unit notification registration:\n"`
    buffer="${buffer} `echo -e "curl --insecure -H \"Authorization: Basic ${CREDENTIALS}\" -H \"Accept: application/xml\" -H \"Content-Type: application/xml\" -X PUT -d @/$put_file $HERD_REST_URL/$STORAGE_PUT_URI$STORAGE_NOTIFICATION_NAME"`"

    curl --insecure -H "Authorization: Basic ${CREDENTIALS}" -H "Accept: application/xml" -H "Content-Type: application/xml" -X PUT -d @$put_file $HERD_REST_URL/$STORAGE_PUT_URI$STORAGE_NOTIFICATION_NAME -s > /tmp/metastor_storage_put_response.xml 2>> $logfile

    parse_xml_response "/tmp/metastor_storage_put_response.xml"

    status=$?
    if [ "$status" == 0 ]
    then
       echo -e "\n----------\nPut archiving notification curl command: successful" >> $logfile
    else
        echo -e "$buffer" >> $logfile
        buffer=""
        buffer=`echo -e "----------\nPOST NOTIFICATION: Running curl post command to create the storage unit notification registration:\n"`
        buffer="${buffer} `echo -e "curl --insecure -H \"Authorization: Basic ${CREDENTIALS}\" -H \"Accept: application/xml\" -H \"Content-Type: application/xml\" -X POST -d @/$post_file $HERD_REST_URL/$STORAGE_POST_URI"`"

        curl --insecure -H "Authorization: Basic ${CREDENTIALS}" -H "Accept: application/xml" -H "Content-Type: application/xml" -X POST -d @$post_file $HERD_REST_URL/$STORAGE_POST_URI -s > /tmp/metastor_storage_post_response.xml 2>> $logfile

        parse_xml_response "/tmp/metastor_storage_post_response.xml"
        status=$?

        if [ "$status" == 0 ]
        then
            echo -e "\n----------\nPost notification curl command: successful" >> $logfile
        else
            echo -e "$buffer" >> $logfile
            echo -e "\nFull curl command output:\n"     >> $logfile
            cat /tmp/metastor_storage_post_response.xml >> $logfile
        fi
        buffer=""
    fi

}

# This delete storage unit notification
function delete_storage_unit_notification() {
	DELETE_NOTIFICATION_NAME=$1
	RESPONSE_FILE_NAME="/tmp/${DELETE_NOTIFICATION_NAME}_metastor_su_delete_response.xml"

	buffer=`echo -e "----------\nDELETE STORAGE UNIT NOTIFICATION: Running curl delete command to delete storage Unit only notification registration:\n"`
    buffer="${buffer} `echo -e "curl --insecure -H \"Authorization: Basic ${CREDENTIALS}\" -H \"Accept: application/xml\" -X DELETE $HERD_REST_URL/$STORAGE_DELETE_URI/${DELETE_NOTIFICATION_NAME}"`"

	# DELETE STORAGE NOTIFICATION
	curl --insecure -H "Authorization: Basic ${CREDENTIALS}" -H "Accept: application/xml" -X DELETE $HERD_REST_URL/$STORAGE_DELETE_URI/${DELETE_NOTIFICATION_NAME} -s > ${RESPONSE_FILE_NAME} 2>> $logfile

	parse_xml_response "${RESPONSE_FILE_NAME}"
	status=$?

	SUCCESS="\n----------\nDelete ${DELETE_NOTIFICATION_NAME} notification curl command: successful"
	if [ "$status" == 0 ]
	then
		echo -e ${SUCCESS} >> $logfile
	else
		echo -e "$buffer" >> $logfile
		echo -e "\nFull curl command output:\n" >> $logfile
		cat ${RESPONSE_FILE_NAME} >> $logfile
	fi
	buffer=""

	sleep 1
}

function back_load_submit_status(){
	code=$1
	if [ "$ACTION" = "add" ] || [ "$ACTION" = "enable" ]
	then
		if [ $code -gt 0 ]
		then
			back_load_status_by_line="${back_load_status_by_line}\n$line_count: $line - Not submitted to Backload partitions due to warning/error with object registration"
		else
			if [ "$WORKFLOW_TYPE" = "DM Managed" ] || [ "$WORKFLOW_TYPE" = "Singleton" ]
			then
				back_load_status_by_line="${back_load_status_by_line}\n$line_count: $line - Submitted to Backload partitions"
				back_load_notifications_arr+=(${NOTIFICATION_NAME})
			else
				back_load_status_by_line="${back_load_status_by_line}\n$line_count: $line - Invalid Workflow Type for Backload partitions"
			fi
		fi
	else
		back_load_status_by_line="${back_load_status_by_line}\n$line_count: $line - Not submitted to Backload due to invalid action for Backload partitions"
	fi
}

#-----------------------------------------------------------------------------------------------------------------#
#                                       MAIN SECTION OF SCRIPT                                                    #
#-----------------------------------------------------------------------------------------------------------------#

if [ -z "$JIRA_ISSUE" ]
then
    echo "Expected JIRA issue input parameter is missing. Exiting script."
    exit 1
fi

# generate logfile based on ENV, app and date
logfile="object_registration.log"
back_load_partitions_log_file="back_load_partitions.log"
back_load_partitions_status_file="/tmp/back_load_partitions.status"
warnings_file="/tmp/$MDL_METASTOR_NAMESPACE.object_registration.warnings"


#--------------------------------------------------------------------------------------------------------#
#                                         START OF DEPLOYMENT ACTIONS                                    #
#--------------------------------------------------------------------------------------------------------#

##############################################################
# Run aws command to enable access to the credentials-store:
echo -e "\n##########\n\nRunning aws command to enable access to the credentials-store: aws configure set s3.signature_version s3v4\n" >> $logfile
aws configure set s3.signature_version s3v4

CREDENTIALS=`cat $HERD_CREDENTIAL`
if [[ -z $CREDENTIALS ]]
then
	echo "Could not get Service Account Credentials, EXITING!!!"
	exit 1
fi

##############################################################

if [ -f "$text_file" ]
then
    # Strip out a) comment lines, b) blank lines and c) the formatting line that starts with namespace
    egrep -v '(^[[:space:]]*#|^[[:space:]]*$|^[[:space:]]*namespace)' $text_file > $text_file_stripped

    # Print all of the lines to be processed to the logfile.
    echo -e "##########\n\nLines to be processed from the $text_file file:\n" >> $logfile

    while read line_a
    do
        line_count=$((line_count+1))
        echo "$line_count: $line_a" >> $logfile
    done < $text_file_stripped

    # Re-set line_count back to 0
    line_count=0

else
    echo -e "\nCould not read the $text_file file, exiting." >> $logfile
    exit 1
fi

##############################################################

while read line
do
    echo -e "\n##############################################\n\nProcessing Line: $line" >> $logfile
    line_count=$((line_count+1))

    # Re-set the warning_count and JSON_TEXT vars so we start fresh for each new line
    warning_count=0
    JSON_TEXT=""
    JSON_FILE=""
    json_file_content=""

    # Call the check_for_bad_chars subroutine to... If bad chars are present, mark it an move on
    check_for_bad_chars line "$line"
    status=$?

    if [ "$status" == 0 ]
    then
        echo -e "\nNo bad chars in line"
    else
        echo -e "\nLine contains bad chars." >> $logfile
        warning_count=$((warning_count+1))
        warnings_detected=$((warnings_detected+1))
        errors_detected=$((errors_detected+1))
        list_of_warnings_by_line="${list_of_warnings_by_line}\n$line_count: $line - Line contains bad characters."
        continue
    fi

    # Set params based on values in the current line of the prop file. The pipe to perl -lape 's///g' removes leading and trailing spaces from these params.
    NAMESPACE=`echo $line | awk 'BEGIN {FS="|"}{print $1}' | perl -lape 's/^\s+|\s+$//g'`
    BO_DEFINITION_NAME=`echo $line | awk 'BEGIN {FS="|"}{print $2}' | perl -lape 's/^\s+|\s+$//g'`
    BO_FORMAT_USAGE=`echo $line | awk 'BEGIN {FS="|"}{print $3}' | perl -lape 's/^\s+|\s+$//g'`
    BO_FORMAT_FILE_TYPE=`echo $line | awk 'BEGIN {FS="|"}{print $4}' | perl -lape 's/^\s+|\s+$//g'`
    WORKFLOW_TYPE=`echo $line | awk 'BEGIN {FS="|"}{print $5}' | perl -lape 's/^\s+|\s+$//g'`
    ACTION=`echo $line | awk 'BEGIN {FS="|"}{print $6}' | perl -lape 's/^\s+|\s+$//g'`
    JSON_FILE=`echo $line | awk 'BEGIN {FS="|"}{print $7}' | perl -lape 's/^\s+|\s+$//g'`

    # echo statements for the logfile:
    echo "NAMESPACE=$NAMESPACE"                     >> $logfile
    echo "BO_DEFINITION_NAME=$BO_DEFINITION_NAME"   >> $logfile
    echo "BO_FORMAT_USAGE=$BO_FORMAT_USAGE"         >> $logfile
    echo "BO_FORMAT_FILE_TYPE=$BO_FORMAT_FILE_TYPE" >> $logfile
    echo "WORKFLOW_TYPE=$WORKFLOW_TYPE"             >> $logfile
    echo "ACTION=$ACTION"                           >> $logfile
    echo -e "JSON_FILE=$JSON_FILE\n"                >> $logfile

    if [ -z "$NAMESPACE" ] || [ -z "$BO_DEFINITION_NAME" ] || [ -z "$BO_FORMAT_USAGE" ] || [ -z "$BO_FORMAT_FILE_TYPE" ] || [ -z "$WORKFLOW_TYPE" ] || [ -z "$ACTION" ]
    then
        echo -e "\nMissing expected parameter from line. Each line should contain 6 pipe delimited values:" >> $logfile
        echo -e "\nnamespace|businessObjectDefinitionName|businessObjectFormatUsage|businessObjectFormatFileType|Workflow Type|Action|" >> $logfile
        warning_count=$((warning_count+1))
        warnings_detected=$((warnings_detected+1))
        errors_detected=$((errors_detected+1))
        list_of_warnings_by_line="${list_of_warnings_by_line}\n$line_count: $line - Missing expected parameter from line."
        continue
    fi

    # Combine the first four params to form the NOTIFICATION_NAME param
    NOTIFICATION_NAME="${NAMESPACE}_${BO_DEFINITION_NAME}_${BO_FORMAT_USAGE}_${BO_FORMAT_FILE_TYPE}"

    # Use sed to replace any periods ("."), hypens ("-") or spaces in the notification name with underscores ("_") as the ETL library we are using
    # does not handle these well at this time.
    NOTIFICATION_NAME=`echo $NOTIFICATION_NAME | sed 's/\./_/g'`

    NOTIFICATION_NAME=`echo $NOTIFICATION_NAME | sed 's/\-/_/g'`

    NOTIFICATION_NAME=`echo $NOTIFICATION_NAME | sed 's/ /_/g'`

    # Call the check_for_bad_chars subroutine again to... If bad chars are present, mark it an move on
    check_for_bad_chars notification_name "$NOTIFICATION_NAME"
    status=$?

    if [ "$status" == 0 ]
    then
        echo -e "\nNo bad chars in notification name $NOTIFICATION_NAME. OK to proceed"
    else
        echo -e "\nNotification name contains bad chars." >> $logfile
        warning_count=$((warning_count+1))
        warnings_detected=$((warnings_detected+1))
        errors_detected=$((errors_detected+1))
        list_of_warnings_by_line="${list_of_warnings_by_line}\n$line_count: $line - Notification name contains bad characters."
        continue
    fi

    NOTIFICATION_NAME_STORAGE_ARCHIVING=${NOTIFICATION_NAME}_ARCHIVE
    NOTIFICATION_NAME_STORAGE_RESTORING=${NOTIFICATION_NAME}_RESTORE
    NOTIFICATION_NAME_STORAGE_EXPIRING=${NOTIFICATION_NAME}_EXPIRE

    # If a json file is provided as a parameter in this line (and this is an "add" action), then verify that the file is present as
    # expected and obtain the content of the file for later use.
    if [ "X$JSON_FILE" != "X" ]
    then
        # A json file was provided. Validate that it exists.
        if [ -f "json_files/$JSON_FILE" ]
        then
            echo "The expected json file is present. Validating format before adding its content to the template." >> $logfile

            # Capture the content of the json file
            json_file_content=`cat json_files/$JSON_FILE`

            # Use the python json.tool to validate that the json file is formatted correctly.
            json_tool_response=`cat json_files/$JSON_FILE | $python -m json.tool 2>&1`
            status=$?

            if [ $status -ne "0" ]
            then
                echo -e "\nThe json file contains the following formatting errors:\n\n$json_tool_response" >> $logfile
                echo -e "\nContent of json file:\n\n$json_file_content" >> $logfile
                echo -e "\nDue to the json formatting errors, this object will not be registered." >> $logfile

                warning_count=$((warning_count+1))
                warnings_detected=$((warnings_detected+1))
                errors_detected=$((errors_detected+1))
                list_of_warnings_by_line="${list_of_warnings_by_line}\n$line_count: $line - The json file contains formatting errors."
                continue
            else
                echo -e "\nThe json file is formatted correctly." >> $logfile
                # Set the JSON_TEXT variable based on the content of the json file.
                JSON_TEXT="<correlationData><![CDATA["
                JSON_TEXT="${JSON_TEXT}$json_file_content"
                JSON_TEXT="${JSON_TEXT}]]></correlationData>"

                echo -e "\nText being added to the template file:\n\n$JSON_TEXT\n" >> $logfile
            fi
        else
            echo "The expected json file is not present. As a result, this object will not be registered." >> $logfile
            warning_count=$((warning_count+1))
            warnings_detected=$((warnings_detected+1))
            errors_detected=$((errors_detected+1))
            list_of_warnings_by_line="${list_of_warnings_by_line}\n$line_count: $line - Expected json file not present."
            continue
        fi
    fi

    # Set the WF_TYPE param accordingly. This param will be used in the sed command that follows.
    if [ "$WORKFLOW_TYPE" = "DM Managed" ]
    then
        WF_TYPE=addPartitionWorkflow
    elif [ "$WORKFLOW_TYPE" = "Singleton" ]
    then
        WF_TYPE=singletonObjectsAddPartitionWorkflow
    else
        echo -e "\nInvalid workflow type provided: $WORKFLOW_TYPE" >> $logfile
        echo -e "\nUpdate txt file and try again. Valid workflow types are: \"DM Managed\" and \"Singleton\"\n" >> $logfile
        warning_count=$((warning_count+1))
        warnings_detected=$((warnings_detected+1))
        errors_detected=$((errors_detected+1))
        list_of_warnings_by_line="${list_of_warnings_by_line}\n$line_count: $line - Invalid workflow type provided: $WORKFLOW_TYPE "
        continue
    fi

    # Check that the ACTION param matches either "add", "drop", "enable", "disable"
    if [ "$ACTION" != "add" ] && [ "$ACTION"  != "drop" ] && [ "$ACTION" != "enable" ] && [ "$ACTION"  != "disable" ]
    then
        echo -e "\nInvalid action provided: $ACTION" >> $logfile
        echo -e "\nUpdate txt file and try again. Valid actions are: \"add\", \"drop\", \"enable\" and \"disable\"\n" >> $logfile
        warning_count=$((warning_count+1))
        warnings_detected=$((warnings_detected+1))
        errors_detected=$((errors_detected+1))
        list_of_warnings_by_line="${list_of_warnings_by_line}\n$line_count: $line - Invalid action provided: $ACTION "
        continue
    fi
    ##########

    # Create the post xml files from the templates for this line overwriting whatever content was previously present.
    cp -f $post_template $post_file     >> $logfile 2>&1
    cp -f $put_template $put_file       >> $logfile 2>&1

    ##########

    # Run sed commands to exchange replacement parameters with values for the current business object.

    # POST FILE:
    sed -i "s/__NOTIFICATION_NAME__/$NOTIFICATION_NAME/g" $post_file     >> $logfile 2>&1
    sed -i "s/__NAMESPACE__/$NAMESPACE/g" $post_file                     >> $logfile 2>&1
    sed -i "s/__BO_FORMAT_FILE_TYPE__/$BO_FORMAT_FILE_TYPE/g" $post_file >> $logfile 2>&1
    sed -i "s/__BO_FORMAT_USAGE__/$BO_FORMAT_USAGE/g" $post_file         >> $logfile 2>&1
    sed -i "s/__BO_DEFINITION_NAME__/$BO_DEFINITION_NAME/g" $post_file   >> $logfile 2>&1
    sed -i "s/__WORKFLOW_TYPE__/$WF_TYPE/g" $post_file                   >> $logfile 2>&1

    # PUT FILE:
    sed -i "s/__NAMESPACE__/$NAMESPACE/g" $put_file                      >> $logfile 2>&1
    sed -i "s/__BO_FORMAT_FILE_TYPE__/$BO_FORMAT_FILE_TYPE/g" $put_file  >> $logfile 2>&1
    sed -i "s/__BO_FORMAT_USAGE__/$BO_FORMAT_USAGE/g" $put_file          >> $logfile 2>&1
    sed -i "s/__BO_DEFINITION_NAME__/$BO_DEFINITION_NAME/g" $put_file    >> $logfile 2>&1
    sed -i "s/__WORKFLOW_TYPE__/$WF_TYPE/g" $put_file                    >> $logfile 2>&1

    # Deal with the enable/disable status:
    if [ "$ACTION" == "enable" ] || [ "$ACTION" == "add" ]
    then
        sed -i "s/__STATUS__/ENABLED/" $post_file  >> $logfile 2>&1
        sed -i "s/__STATUS__/ENABLED/" $put_file   >> $logfile 2>&1
    elif [ "$ACTION" == "disable" ]
    then
        sed -i "s/__STATUS__/DISABLED/" $post_file >> $logfile 2>&1
        sed -i "s/__STATUS__/DISABLED/" $put_file  >> $logfile 2>&1
    fi

    # Call add_json_to_file subroutine for the post_file and the put_file files
    add_json_to_file $post_file
    add_json_to_file $put_file

    ##########

    # Run curl commands to register the business objects.
    #
    # 1. Run curl command to PUT the NAMESPACE object if available
    # 2. Run curl command to GET the NAMESPACE object format if the NAMESPACE object is not available
    # 3. Run curl command to POST the NAMESPACE object if the format is available
    #

    # Define the URI for the POST and DELETE commands
    POST_URI=notificationRegistrations/businessObjectDataNotificationRegistrations
    STORAGE_POST_URI=notificationRegistrations/storageUnitNotificationRegistrations
    PUT_URI=notificationRegistrations/businessObjectDataNotificationRegistrations/namespaces/$MDL_METASTOR_NAMESPACE/notificationNames/$NOTIFICATION_NAME
    STORAGE_PUT_URI=notificationRegistrations/storageUnitNotificationRegistrations/namespaces/$MDL_METASTOR_NAMESPACE/notificationNames/
    DELETE_URI=notificationRegistrations/businessObjectDataNotificationRegistrations/namespaces/$MDL_METASTOR_NAMESPACE/notificationNames/$NOTIFICATION_NAME
    STORAGE_DELETE_URI=notificationRegistrations/storageUnitNotificationRegistrations/namespaces/$MDL_METASTOR_NAMESPACE/notificationNames/

    # Define the URI for the Business Object Format GET commands
    GET_FORMAT_URI=businessObjectFormats/namespaces/$NAMESPACE/businessObjectDefinitionNames/$BO_DEFINITION_NAME/businessObjectFormatUsages/$BO_FORMAT_USAGE/businessObjectFormatFileTypes/$BO_FORMAT_FILE_TYPE

# GET debug command:
#echo -e "\n\nRunning GET curl command for $line to show status prior to executing $ACTION:" >> $logfile
#curl --insecure -H "Authorization: Basic ${CREDENTIALS}" -H "Accept: application/xml" -X GET $DM_REST_URL/$DELETE_URI -s >> $logfile 2>&1

    ##########
    if [ "$ACTION" = "add" ] || [ "$ACTION" = "enable" ] || [ "$ACTION" = "disable" ]
    then
    	put=0
        post=0
        # 1. PUT NOTIFICATION:
        buffer=`echo -e "----------\nPUT NOTIFICATION: Running curl get command to PUT business object data notification registration:\n"`
        buffer="${buffer} `echo -e "curl --insecure -H \"Authorization: Basic ${CREDENTIALS}\" -H \"Accept: application/xml\" -H \"Content-Type: application/xml\" -X PUT -d @/$put_file $HERD_REST_URL/$GET_REG_URI"`" 2>> $logfile

        curl --insecure -H "Authorization: Basic ${CREDENTIALS}" -H "Accept: application/xml" -H "Content-Type: application/xml" -X PUT -d @$put_file $HERD_REST_URL/$PUT_URI -s > /tmp/metastor_bo_put_response.xml 2>> $logfile

		echo -e "Content of the metastor_bo_put_response.xml file:\n" | tee -a $logfile
		cat /tmp/metastor_bo_put_response.xml | tee -a $logfile

        parse_xml_response "/tmp/metastor_bo_put_response.xml"
        status=$?

        if [ "$status" == 0 ]
        then
            echo -e "\n----------\nPut notification curl command: successful"
            put=1
        else

            # 2. GET FORMAT: Do a business object format GET to determine if the table is registered
            buffer=`echo -e "\n----------\nGET Business Object Format: Running curl get command to GET the business object format:\n"`
            buffer="${buffer} `echo -e "curl --insecure -H \"Authorization: Basic ${CREDENTIALS}\" -H \"Accept: application/xml\" -H \"Content-Type: application/xml\" -X GET $HERD_REST_URL/$GET_FORMAT_URI"`" 2>> $logfile

            curl --insecure -H "Authorization: Basic ${CREDENTIALS}" -H "Accept: application/xml" -H "Content-Type: application/xml" -X GET $HERD_REST_URL/$GET_FORMAT_URI -s > /tmp/metastor_bo_get_response.xml 2>> $logfile

			echo -e "Content of the metastor_bo_get_response.xml file:\n" | tee -a $logfile
			cat /tmp/metastor_bo_get_response.xml | tee -a $logfile

            parse_xml_response "/tmp/metastor_bo_get_response.xml"

            status=$?

            if [ "$status" == 0 ]
            then
                echo -e "\n----------\nGet notification curl command: successful"
                post=1
            elif [ "$status" == 2 ]
            then
                echo -e "\nThe schema information was not found in the Business Object Format Registration, register schema information and try again." >> $logfile
            else
                echo -e "$buffer" >> $logfile
                echo -e "\nFull curl command output:\n" >> $logfile
                cat /tmp/metastor_bo_get_response.xml   >> $logfile
            fi

            buffer=""
        fi

        ##########

        if [ $post -eq 1 ]
        then
            # 3. POST NOTIFICATION:
            buffer=`echo -e "----------\nPOST NOTIFICATION: Running curl post command to create the business object data notification registration:\n"`
            buffer="${buffer} `echo -e "curl --insecure -H \"Authorization: Basic ${CREDENTIALS}\" -H \"Accept: application/xml\" -H \"Content-Type: application/xml\" -X POST -d @/$post_file $HERD_REST_URL/$POST_URI"`"

            curl --insecure -H "Authorization: Basic ${CREDENTIALS}" -H "Accept: application/xml" -H "Content-Type: application/xml" -X POST -d @$post_file $HERD_REST_URL/$POST_URI -s > /tmp/metastor_bo_post_response.xml  2>> $logfile

            parse_xml_response "/tmp/metastor_bo_post_response.xml"
            status=$?

            if [ "$status" == 0 ]
            then
                echo -e "\n----------\nPost notification curl command: successful"
            else
                echo -e "$buffer" >> $logfile
                echo -e "\nFull curl command output:\n" >> $logfile
                cat /tmp/metastor_bo_post_response.xml  >> $logfile
            fi

            buffer=""

		fi

		# Run Storage unit Notification Registration only when Put or Post are successful

		################## Storage Unit Notification Processing #################
		if [ $put -eq 1 ] || [ $post -eq 1 ]
		then
			if [ $WF_TYPE != "singletonObjectsAddPartitionWorkflow" ]
			then
				# Storage Unit notification registration
				register_storage_unit_notification ${NOTIFICATION_NAME_STORAGE_ARCHIVING} ARCHIVING
    			register_storage_unit_notification ${NOTIFICATION_NAME_STORAGE_RESTORING} RESTORED
    			register_storage_unit_notification ${NOTIFICATION_NAME_STORAGE_EXPIRING} EXPIRING

			fi
		fi
    elif [ "$ACTION" = "drop" ]
    then
        # 1. DELETE NOTIFICATION:
        buffer=`echo -e "----------\nDELETE NOTIFICATION: Running curl delete command to delete the business object data notification registration:\n"`
        buffer="${buffer} `echo -e "curl --insecure -H \"Authorization: Basic ${CREDENTIALS}\" -H \"Accept: application/xml\" -X DELETE $HERD_REST_URL/$DELETE_URI"`"

        curl --insecure -H "Authorization: Basic ${CREDENTIALS}" -H "Accept: application/xml" -X DELETE $HERD_REST_URL/$DELETE_URI -s > /tmp/metastor_bo_delete_response.xml  2>> $logfile

		echo -e "Content of the metastor_bo_delete_response.xml file:\n" | tee -a $logfile
		cat /tmp/metastor_bo_delete_response.xml | tee -a $logfile

        parse_xml_response "/tmp/metastor_bo_delete_response.xml"
        status=$?

        if [ "$status" == 0 ]
        then
            echo -e "\n----------\nDelete notification curl command: successful"
        else
            echo -e "$buffer" >> $logfile
        fi

        sql="insert into DM_NOTIFICATION (NAMESPACE,OBJECT_DEF_NAME,USAGE_CODE,FILE_TYPE,WF_TYPE, EXECUTION_ID, PARTITION_VALUES) VALUES "
        sql+="('${NAMESPACE}','${BO_DEFINITION_NAME}','${BO_FORMAT_USAGE}','${BO_FORMAT_FILE_TYPE}','7','drop','');"

        mysql -h $RDS_HOST -P 3306 -u $DB_USER --password=$DB_PWD --ssl-ca=/etc/aws-rds/ssl/rds-combined-ca-bundle.pem metastor -e "$sql"
        cluster_template="emrClusterRequest.xml"
        cluster_post=/tmp/cluster_post.xml
        cp -f $cluster_template $cluster_post    >> $logfile 2>&1

        sed -i "s/__CLUSTER_DEF__/${CLUSTER_DEF}/g" $cluster_post >> $logfile 2>&1
        sed -i "s/__CLUSTER_NAME__/${CLUSTER_NAME}/g" $cluster_post >> $logfile 2>&1

        echo -e "Curl request to create a cluster" 2>&1 >> $logfile
        curl --insecure -H "Authorization: Basic $CREDENTIALS" -H "Accept: application/xml" \
        -H "Content-Type: application/xml" -X POST -d @$cluster_post $HERD_REST_URL/emrClusters -s

        sleep 1
        buffer=""

		# DELETE STORAGE NOTIFICATION
    	delete_storage_unit_notification ${NOTIFICATION_NAME_STORAGE_ARCHIVING}
    	delete_storage_unit_notification ${NOTIFICATION_NAME_STORAGE_RESTORING}
    	delete_storage_unit_notification ${NOTIFICATION_NAME_STORAGE_EXPIRING}

        ##########
    fi

    if [ "$ACTION" = "add" ] || [ "$ACTION" = "enable" ] || [ "$ACTION" = "drop" ]
    then
        # Delete the email notification
        buffer=`echo -e "----------\nDELETE EMAIL NOTIFICATION: Running curl delete command to delete email only notification registration:\n"`
        buffer="${buffer} `echo -e "curl --insecure -H \"Authorization: Basic ${CREDENTIALS}\" -H \"Accept: application/xml\" -X DELETE $HERD_REST_URL/${DELETE_URI}_EMAIL"`"

        curl --insecure -H "Authorization: Basic ${CREDENTIALS}" -H "Accept: application/xml" -X DELETE $HERD_REST_URL/${DELETE_URI}_EMAIL -s > /tmp/metastor_bo_email_delete_response.xml 2>> $logfile

        parse_xml_response "/tmp/metastor_bo_email_delete_response.xml"
        status=$?

        if [ "$status" == 0 ]
        then
             echo -e "\n----------\nDelete email notification curl command: successful" >> $logfile
        else
             echo -e "$buffer" >> $logfile
        fi

        buffer=""

    elif [ "$ACTION" = "disable" ]
    then
        post_file=post_bo_email_notification_registration.xml
        cp -f $post_template $post_file                                          >> $logfile 2>&1
        sed -i "s/__NOTIFICATION_NAME__/${NOTIFICATION_NAME}_EMAIL/g" $post_file >> $logfile 2>&1
        sed -i "s/__NAMESPACE__/$NAMESPACE/g" $post_file                         >> $logfile 2>&1
        sed -i "s/__BO_FORMAT_FILE_TYPE__/$BO_FORMAT_FILE_TYPE/g" $post_file     >> $logfile 2>&1
        sed -i "s/__BO_FORMAT_USAGE__/$BO_FORMAT_USAGE/g" $post_file             >> $logfile 2>&1
        sed -i "s/__BO_DEFINITION_NAME__/$BO_DEFINITION_NAME/g" $post_file       >> $logfile 2>&1
        sed -i "s/__WORKFLOW_TYPE__/emailDisabledObjectsWorkflow/g" $post_file   >> $logfile 2>&1
        sed -i "s/__STATUS__/ENABLED/" $post_file                                >> $logfile 2>&1
        sed -i "s/__JSON__//" $post_file                                         >> $logfile 2>&1

        # POST THE EMAIL NOTIFICATION:
        buffer=`echo -e "----------\nPOST NOTIFICATION: Running curl post command to create the email object data notification registration:\n"`
        buffer="${buffer} `echo -e "curl --insecure -H \"Authorization: Basic ${CREDENTIALS}\" -H \"Accept: application/xml\" -H \"Content-Type: application/xml\" -X POST -d @/$post_file $HERD_REST_URL/$POST_URI"`"

        curl --insecure -H "Authorization: Basic ${CREDENTIALS}" -H "Accept: application/xml" -H "Content-Type: application/xml" -X POST -d @$post_file $HERD_REST_URL/$POST_URI -s > /tmp/metastor_bo_email_post_response.xml 2>> $logfile

        parse_xml_response "/tmp/metastor_bo_email_post_response.xml"
        status=$?

        if [ "$status" == 0 ]
        then
            echo -e "\n----------\nPost email notification curl command: successful" >> $logfile
        else
            echo -e "$buffer" >> $logfile
            echo -e "\nFull curl command output:\n"      >> $logfile
            cat /tmp/metastor_bo_email_post_response.xml >> $logfile
        fi

        buffer=""
     fi

# GET debug command:
#echo -e "\n\nRunning GET curl command for $line to show status after executing $ACTION:" >> $logfile
#curl --insecure -H "Authorization: Basic ${CREDENTIALS}" -H "Accept: application/xml" -X GET $DM_REST_URL/$DELETE_URI -s >> $logfile

    ##############

    if [ $warning_count -gt 0 ]
    then
        warnings_detected=$((warnings_detected+1))
        list_of_warnings_by_line="${list_of_warnings_by_line}\n$line_count: $line - Warning Message: $error_message"

        back_load_submit_status 1
    else
        echo -e "Curl commands run for the current line were successful." >> $logfile
        back_load_submit_status 0
    fi

done < $text_file_stripped

##############################################################

passemail
