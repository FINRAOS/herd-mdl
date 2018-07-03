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

# Create certificates, if requested
if [ "${enableSSLAndAuth}" = "true" ] ; then
    execute_cmd "mkdir -p /home/mdladmin/certs"
    execute_cmd "cd /home/mdladmin/certs"

    execute_cmd "sed -i \"s/{{region}}/${region}/g\" ${deployLocation}/conf/openssl.conf"

    # Get the Certificate information
    echo ${certificateInfo} | tr "," "\n" | while read LINE
    do
        if [ "${LINE:0:3}" = "CN=" ] ; then
            execute_cmd "sed -i \"s/{{mdlDomain}}/${domainNameSuffix}/g\" ${deployLocation}/conf/openssl.conf"
        else
            echo ${LINE} >> ${deployLocation}/conf/openssl.conf
        fi
    done

    export SSL_KEY_SIZE=2048
    export SSL_EXPIRE_DAYS=1095
    export SSL_KEY_NAME=mdl
    export SSL_PASS=changeit

    # Create Root CA and RSA certificates
    execute_cmd "sudo openssl genrsa -out rootCA.key ${SSL_KEY_SIZE}"
    execute_cmd "sudo openssl req -x509 -new -nodes -key rootCA.key -days ${SSL_EXPIRE_DAYS} -out rootCA.crt -config ${deployLocation}/conf/openssl.conf"
    execute_cmd "sudo openssl genrsa -out ${SSL_KEY_NAME}.key ${SSL_KEY_SIZE}"
    execute_cmd "sudo openssl req -new -key ${SSL_KEY_NAME}.key -out ${SSL_KEY_NAME}.csr -config ${deployLocation}/conf/openssl.conf"
    execute_cmd "sudo openssl x509 -req -in ${SSL_KEY_NAME}.csr -CA rootCA.crt -CAkey rootCA.key -CAcreateserial -out ${SSL_KEY_NAME}.crt -days ${SSL_EXPIRE_DAYS} -extfile ${deployLocation}/conf/openssl.conf -extensions req_ext"
    execute_cmd "sudo openssl pkcs12 -export -in ${SSL_KEY_NAME}.crt -inkey ${SSL_KEY_NAME}.key -out ${SSL_KEY_NAME}.p12 -name ${SSL_KEY_NAME} -CAfile rootCA.crt -caname root -passout pass:${SSL_PASS}"
    execute_cmd "sudo keytool -v -importkeystore -deststorepass ${SSL_PASS} -destkeypass ${SSL_PASS} -destkeystore ${SSL_KEY_NAME}.jks -deststoretype JKS -srckeystore ${SSL_KEY_NAME}.p12 -srcstoretype PKCS12 -alias ${SSL_KEY_NAME} -srcstorepass ${SSL_PASS}"

    # Prepare the certificates
    openssl x509 -in ${SSL_KEY_NAME}.crt -text | sed -n -e '/---BEGIN/,$p' > ./mdlCertificate.pem
    openssl x509 -in rootCA.crt -text | sed -n -e '/---BEGIN/,$p' > ./mdlCChain.pem
    openssl rsa -in ./mdl.key -text | sed -n -e '/---BEGIN/,$p' > ./mdl.pem

    # Upload the certificates
    cmdOutput=`aws acm import-certificate --certificate mdl --certificate file://mdlCertificate.pem --private-key file://mdl.pem --certificate-chain file://mdlCChain.pem --region ${region}`
    echo $cmdOutput
    certificateArn=`echo ${cmdOutput}| jq '.CertificateArn' |sed -e 's/"//g'`

    # Validate the uploaded certificate and fail, if invalid
    execute_cmd "aws acm describe-certificate --certificate-arn ${certificateArn} --region ${region}"

    # Copy certs to s3
    execute_cmd "aws s3 cp --recursive . s3://${mdlStagingBucketName}/certs"

    # Upload the parameter information to SSM
    execute_cmd "aws ssm put-parameter --name /app/MDL/${mdlInstanceName}/${environment}/ACM/Arn --type "String" --value ${certificateArn} --region ${region} --overwrite"
fi

exit 0
