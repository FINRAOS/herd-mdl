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

# Curl commands need to be retried multiple times to avoid network errors
function execute_curl_cmd {
  mdlUserLdapPassword=$(aws ssm get-parameter --name ${ldapMdlAppUserPasswordParameterKey} --with-decryption --region ${region} --output text --query Parameter.Value)
	cmd="${1} --retry 5 --max-time 120 --retry-delay 7 --write-out \"\nHTTP_CODE:%{http_code}\n\" -u ${ldapMdlAppUsername}:${mdlUserLdapPassword}"
	echo "${1} --retry 5 --max-time 120 --retry-delay 7 --write-out \"\nHTTP_CODE:%{http_code}\n\" "
	eval $cmd > /tmp/curlCmdOutput 2>&1
	echo ""
	returnCode=`cat /tmp/curlCmdOutput | grep "HTTP_CODE" | cut -d":" -f2`

  if [ "${returnCode}" != "200" ]; then
      echo "$(date "+%m/%d/%Y %H:%M:%S") *** ERROR *** ${1} has failed with error ${returnCode}"
    	cat /tmp/curlCmdOutput
    	echo ""
      exit 1
  fi
}


#MAIN
configFile="/home/mdladmin/deploy/mdl/conf/deploy.props"
if [ ! -f ${configFile} ] ; then
    echo "Config file does not exist ${configFile}"
    exit 1
fi
. ${configFile}

execute_cmd "cd /home/mdladmin"
execute_cmd "aws configure set default.region ${region}"

#Configure cloudwatch log for herd
execute_cmd "sed -i \"s/{log_group_name}/${logGroupName}/g\" ${deployLocation}/conf/logs.conf"
execute_cmd "sudo bash -c 'echo >> /var/awslogs/etc/config/codedeploy_logs.conf; cat /home/mdladmin/deploy/mdl/conf/logs.conf >> /var/awslogs/etc/config/codedeploy_logs.conf'"
execute_cmd "sudo service awslogs restart"

# Copy stack tags to Sqs & Cloudfront
function addStackTagsToSqs(){
    stack_tags=$(aws cloudformation describe-stacks --stack-name ${stackName} --query "Stacks[*].Tags" --output json | jq -c '.[]')
    isTagExisted=$( echo jq -r '.[]' | jq 'any' <<<"${stack_tags}" )

    if [ "${isTagExisted}" != "false" ] ; then
        echo "tagging sqs"
        sqs_tags=$( echo jq -r '.[]' | jq 'from_entries' <<<"${stack_tags}" )
        sqs_tags=${sqs_tags//\"/\\\"}
        herdInQueueUrl=$(aws sqs get-queue-url --queue-name ${herdQueueInName} | jq -r '.QueueUrl')
        esearchQueueUrl=$(aws sqs get-queue-url --queue-name ${searchIndexUpdateSqsQueueName} | jq -r '.QueueUrl')
        execute_cmd "aws sqs tag-queue --queue-url ${herdInQueueUrl} --tags \"$sqs_tags\""
        execute_cmd "aws sqs tag-queue --queue-url ${esearchQueueUrl} --tags \"$sqs_tags\""
    fi
}

function addStackTagsToCloudFront(){
    stack_tags=$(aws cloudformation describe-stacks --stack-name tagsqs --query "Stacks[*].Tags" --output json | jq -c '.[]')
    #TODO: difficulty in getting cloudfront arn by cli get command directly
}

execute_cmd "echo \"From $0\""

if [ "${CreateSQS}" == 'true' ] ; then
    addStackTagsToSqs
fi

# Register the target instance to the ELB
targetGroupArn=` aws elbv2 describe-target-groups --region ${region} --names ${mdlInstanceName}-HerdTargetGroup | grep TargetGroupArn | cut -d":" -f2- | tr -d '\"' | tr -d ',' | tr -d ' '`
instanceId=`curl http://169.254.169.254/latest/meta-data/instance-id`
execute_cmd "aws elbv2 register-targets --target-group-arn ${targetGroupArn} --targets Id=${instanceId} --region ${region}"

# Configure tomcat and start
execute_cmd "sudo ${deployLocation}/scripts/configureTomcat.sh"
execute_cmd "sudo tomcat8 start"
sleep 30

# Configure apache and start
execute_cmd "sudo ${deployLocation}/scripts/configureApache.sh"
execute_cmd "sudo /etc/init.d/httpd start"
sleep 30

if [ "${refreshDatabase}" = "true" ] ; then
    # Replace the storage with correct values
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -X DELETE ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/storages/S3_MANAGED --insecure"
    execute_cmd "sed -i \"s/{{BUCKET_NAME}}/${herdS3BucketName}/g\" ${deployLocation}/xml/install/s3ManagedStorage.xml"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/install/s3ManagedStorage.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/storages --insecure"

    # Run System job
    execute_curl_cmd "curl ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/buildInfo --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/install/systemJob.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/systemJobs --insecure"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @${deployLocation}/xml/install/partitionKeyGroup.xml -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/partitionKeyGroups --insecure"

    # Create bdef and tag indices in elasticsearch and activate them
    execute_curl_cmd "curl --request POST --header 'Content-Type: application/json' --data '{\"searchIndexKey\":{\"searchIndexName\":\"bdef\"},\"searchIndexType\":\"BUS_OBJCT_DFNTN\"}' ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/searchIndexes --insecure"
    indexName=`cat /tmp/curlCmdOutput | grep 'searchIndex' | xmllint --xpath 'string(/searchIndex/searchIndexKey/searchIndexName)' -`
    echo "Business object definition index -> ${indexName}"
    execute_cmd "cp ${deployLocation}/xml/demo/searchIndexActivate.xml /tmp/searchIndexActivate.xml.subst"
    execute_cmd "sed -i \"s/{{INDEX_NAME}}/${indexName}/g\" /tmp/searchIndexActivate.xml.subst"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @/tmp/searchIndexActivate.xml.subst -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/searchIndexActivations --insecure"

    execute_curl_cmd "curl --request POST --header 'Content-Type: application/json' --data '{\"searchIndexKey\":{\"searchIndexName\":\"tag\"},\"searchIndexType\":\"TAG\"}' ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/searchIndexes --insecure"
    indexName=`cat /tmp/curlCmdOutput | grep 'searchIndex' | xmllint --xpath 'string(/searchIndex/searchIndexKey/searchIndexName)' -`
    echo "Tag index -> ${indexName}"
    execute_cmd "cp ${deployLocation}/xml/demo/searchIndexActivate.xml /tmp/searchIndexActivate.xml.subst"
    execute_cmd "sed -i \"s/{{INDEX_NAME}}/${indexName}/g\" /tmp/searchIndexActivate.xml.subst"
    execute_curl_cmd "curl -H 'Content-Type: application/xml' -d @/tmp/searchIndexActivate.xml.subst -X POST ${httpProtocol}://${herdLoadBalancerDNSName}/herd-app/rest/searchIndexActivations --insecure"
fi

# Execute the demo script if asked
if [ "${createDemoObjects}" = "true" ] ; then
    execute_cmd "${deployLocation}/scripts/demoHerd.sh"
    execute_cmd "${deployLocation}/scripts/demoES.sh"
fi

# Signal to Cloud Stack
execute_cmd "/opt/aws/bin/cfn-signal -e 0 -r 'Herd Creation Complete' \"${waitHandleForHerd}\""

echo "Everything looks good"

exit 0
