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
#!/usr/bin/env python
import json
import logging

import boto3
from botocore.exceptions import ClientError
from botocore.vendored import requests

SUCCESS = "SUCCESS"
FAILED = "FAILED"

logger = logging.getLogger()
handler = logging.StreamHandler()
formatter = logging.Formatter('[%(asctime)s %(levelname)-8s] %(message)s')
handler.setFormatter(formatter)
logger.addHandler(handler)
logger.setLevel(logging.INFO)


# Lambda function 'script' which creates/destroys an EC2 Keypair with a user-specified name. It also stores the
# private key material as a 'SecureString' parameter in SSM's parameter store. This is packaged to work with an AWS
# 'CustomResource'. Further reading here: https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/template
# -custom-resources-lambda.html


# Entry-point of script which is invoked by the Lambda function
def handler(event, context):
    logger.info('Request: Event: \n {}'.format(event))
    logger.info('Request: Context: \n {}'.format(context))

    # Get the keypair name as defined in the Resource properties and convert to lowercase for consistency
    keypair_name = construct_keypair_name(event)
    keypair_ssm_key_name = str(event['ResourceProperties']['KeypairSsmKeyName'])
    physical_resource_id = str(event['LogicalResourceId']) + '-' + keypair_name

    # On stack-create, does the following things:
    # 1. Checks if a keypair with the specified name already exists, if it does- skips to step #4.
    # 2. Creates the keypair with the given name.
    # 3. Stores the keypair material in SSM as an encrypted value.
    # 4. Signals CloudFormation that the process is complete.
    if event['RequestType'] == 'Create':

        if not ssm_parameter_exists(keypair_ssm_key_name, event, context, physical_resource_id):
            logger.info(
                'Attempting to create new SSM parameter to store keypair name: \'{}\''.format(keypair_ssm_key_name))
            description = 'keypair name'
            put_parameter_in_ssm(keypair_ssm_key_name, description, keypair_name, 'String', event, context,
                                 physical_resource_id)
        else:
            logger.warning('SSM parameter for key pair name already exists, will not create a new one.')

        if not keypair_exists(keypair_name, event, context, physical_resource_id):
            logger.info('Attempting to create a new keypair: \{}\''.format(keypair_name))
            keypair_material = create_key_pair(keypair_name, event, context,
                                               physical_resource_id)
            description = 'private key material'
            put_parameter_in_ssm(keypair_name, description, keypair_material, 'SecureString', event, context,
                                 physical_resource_id)
            response_data = construct_response_message(
                'Created new keypair: \'{}\' and stored in parameter store.'.format(
                    keypair_name))
            send(event, context, SUCCESS, response_data, physical_resource_id)
        else:
            response_data = construct_response_message(
                'Keypair: \'{}\' already exists, nothing to do.'.format(keypair_name))
            send(event, context, SUCCESS, response_data, physical_resource_id)

    # On stack-delete, do the following things:
    # 1. Checks if a keypair with the specified name already exists, if it does- deletes it.
    # 2. Checks if an SSM parameter exists with the specified name, if it does- deletes it.
    # 3. Signals CloudFormation that the process is complete.
    elif event['RequestType'] == 'Delete':
        message = ''
        if keypair_exists(keypair_name, event, context, physical_resource_id):
            logger.info('Attempting to delete the keypair')
            delete_key_pair(keypair_name, event, context, physical_resource_id)
            message += 'Deleted keypair: \'{}\''.format(keypair_name)

        if ssm_parameter_exists(keypair_name, event, context, physical_resource_id):
            delete_key_pair_parameter_key(keypair_name, event, context,
                                          physical_resource_id)
            message += '\nDeleted parameter with key: \'{}\' from SSM.'.format(
                keypair_name)

            response_data = construct_response_message(message)
        else:
            response_data = construct_response_message(
                'Keypair: \'{}\' and parameter: \'{}\' do not exist. Nothing '
                'to delete.'.format(keypair_name, keypair_name))
        send(event, context, SUCCESS, response_data, physical_resource_id)

    # On stack-update, does nothing and simply exits.
    elif event['RequestType'] == 'Update':
        logger.info('Nothing to update')
        response_data = construct_response_message('Nothing to update')
        send(event, context, SUCCESS, response_data, physical_resource_id)


def construct_keypair_name(event):
    delimiter = '_'
    app_prefix = 'app'
    instance_name = str(event['ResourceProperties']['MDLInstanceName'])
    environment = str(event['ResourceProperties']['Environment'])

    # lower-case the keypair name for consistency
    return delimiter.join([app_prefix, instance_name, environment]).lower()


# Function to check if a keypair exists with the specified name. Returns a Boolean.
def keypair_exists(keypair_name, event, context, physical_resource_id):
    logger.info(
        'Checking if a keypair already exists with the specified name: \'{}\''.format(
            keypair_name))

    try:
        ec2 = boto3.client('ec2')
        response = ec2.describe_key_pairs(
            KeyNames=[
                keypair_name
            ]
        )
        if response['ResponseMetadata']['HTTPStatusCode'] == 200 and len(response['KeyPairs']) == 1:
            logger.warning("KeyPair: \'{}\' found.".format(keypair_name))
            return True
    except ClientError as e:
        if e.response['Error']['Code'] == 'InvalidKeyPair.NotFound':
            logger.info('KeyPair: \'{}\' not found.'.format(keypair_name))
            return False
        else:
            logger.error('Unexpected error: {}'.format(e))
            response_data = construct_response_message(
                'Unexpected error while trying to \'describe\' the Keypair: \'{}\'. Exception: {}'
                .format(keypair_name, e))
            send(event, context, FAILED, response_data, physical_resource_id)
            return False


# Function to check if a key-value pair exists in SSM with the specified name. Returns a Boolean.
def ssm_parameter_exists(key_name, event, context, physical_resource_id):
    logger.info(
        'Checking if a parameter exists in SSM with the specified name: \'{}\''.format(
            key_name))

    try:
        ssm = boto3.client('ssm')
        response = ssm.get_parameter(
            Name=key_name
        )
        if response['ResponseMetadata']['HTTPStatusCode'] == 200:
            logger.info(
                'Found parameter with key name: \'{}\' in SSM.'.format(key_name))
            return True
    except ClientError as e:
        if e.response['ResponseMetadata']['HTTPStatusCode'] == 400:
            logger.info(
                'Parameter with key: \'{}\' not found in SSM.'.format(key_name))
            return False
        else:
            logger.error('Unexpected error: {}'.format(e))
            response_data = construct_response_message(
                'Unexpected error while trying to get parameter: \'{}\'. Exception: {}'.format(
                    key_name, e))
            send(event, context, FAILED, response_data, physical_resource_id)
            return False


# Creates an EC2 keypair with the specified name
def create_key_pair(keypair_name, event, context, physical_resource_id):
    try:
        ec2 = boto3.resource('ec2')

        logging.info(
            'Attempting to create a keypair with name: {}'.format(keypair_name))
        response = ec2.create_key_pair(
            KeyName=keypair_name,
            DryRun=False
        )
        return response.key_material
    except ClientError as e:
        logger.error(
            'Could not create keypair with name: \'{}\'. Exception: {}'.format(
                keypair_name, e))
        response_data = construct_response_message(
            'Unexpected error while trying to create keypair with given name: \'{}\'. Exception: {}'
            .format(keypair_name, e))
        send(event, context, FAILED, response_data, physical_resource_id)


# Stores a specified key-material with a given name in SSM.
def put_parameter_in_ssm(key_name, description, material, value_type, event, context, physical_resource_id):
    logger.info('Attempting to put parameter in SSM with name: \'{}\'.'.format(
        key_name))

    try:
        ssm = boto3.client('ssm')

        response = ssm.put_parameter(
            Name=key_name,
            Description=description,
            Value=material,
            Type=value_type,
            Overwrite=True
        )
        return response
    except ClientError as e:
        logger.error(
            'Could not store key material in SSM with key: \'{}\'. Exception: {}'.format(
                key_name, e))
        response_data = construct_response_message(
            'Unexpected error while trying to \'pur\' parameter in SSM with given name: \'{}\'. Exception: {}'
            .format(key_name, e))
        send(event, context, FAILED, response_data, physical_resource_id)


# Deletes a parameter from SSM of a given name
def delete_key_pair_parameter_key(keypair_name, event, context,
                                  physical_resource_id):
    logger.info(
        'Attempting to delete the key with name: \'{}\''.format(keypair_name))

    try:
        ssm = boto3.client('ssm')
        ssm.delete_parameter(
            Name=keypair_name
        )
        return True
    except ClientError as e:
        logger.error('Could not delete key: \'{}\' from SSM. Exception: {}'.format(
            keypair_name, e))
        response_data = construct_response_message(
            'Unexpected error while trying to \'delete\' parameter from SSM with given name: \'{}\'. Exception: {}'
            .format(keypair_name, e))
        send(event, context, FAILED, response_data, physical_resource_id)
        return False


# Deletes a keypair of a given name
def delete_key_pair(keypair_name, event, context, physical_resource_id):
    logger.info(
        'Attempting to delete the keypair with name: \'{}\''.format(keypair_name))

    try:
        ec2 = boto3.client('ec2')
        ec2.delete_key_pair(
            KeyName=keypair_name
        )
        return True
    except ClientError as e:
        logger.error(
            'Could not delete keypair with name: \'{}\'. Exception: {}'.format(
                keypair_name, e))
        response_data = construct_response_message(
            'Unexpected error while trying to \'delete\' keypair with given name: \'{}\'. Exception: {}'
            .format(keypair_name, e))
        send(event, context, FAILED, response_data, physical_resource_id)
        return False


# Function to construct a formatted response message to send to CloudFormation while signaling it
def construct_response_message(message):
    return {'Message': message}


# Function to signal CloudFormation.
def send(event, context, response_status, response_data, physical_resource_id):
    response_url = event['ResponseURL']

    logger.debug('ResponseURL: {}'.format(response_url))

    responseBody = {'Status': response_status,
                    'Reason': 'See the details in CloudWatch Log Stream: ' + context.log_stream_name,
                    'PhysicalResourceId': physical_resource_id or context.log_stream_name,
                    'StackId': event['StackId'],
                    'RequestId': event['RequestId'],
                    'LogicalResourceId': event['LogicalResourceId'],
                    'NoEcho': 'false',
                    'Data': response_data}

    json_response_body = json.dumps(responseBody)

    logger.debug("Response body: {}".format(json_response_body))

    headers = {
        'content-type': '',
        'content-length': str(len(json_response_body))
    }

    try:
        response = requests.put(response_url,
                                data=json_response_body,
                                headers=headers)
        logger.info("Status code: {}".format(response.reason))
    except Exception as e:
        logger.error("Send failed: {}".format(str(e)))
