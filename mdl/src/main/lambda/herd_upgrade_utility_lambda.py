import os

import boto3
from botocore.exceptions import ClientError

INSTANCE_NAME = os.environ['INSTANCE_NAME']
ENVIRONMENT = os.environ['ENVIRONMENT']
STAGING_BUCKET = os.environ['STAGING_BUCKET']
RELEASE_VERSION = os.environ['RELEASE_VERSION']


def handler(event, context):
    """
    Lambda function which performs a rolling upgrade of a pre-existing Herd EC2 stack in CodeDeploy's B/G style,
    it works in the following manner:
        1. Checks if the 'deployment style' is BLUE_GREEN, if not - updates it.
        2. Configures the deployment 'state' of the pre-existing Herd EC2 stack ny updating a pre-defined set of
           SSM parameter values.
        3. Triggers a deployment.
    To trigger a rolling B/G deployment, invoke this lambda with the following event:

        event={
            'RequestedVersion': '<desired-herd-version>'
        }

    :param event: incoming event to the lambda.
    :param context: ignored
    :return: None
    """
    if event['RequestedVersion'] is None:
        _print_error(
            'FATAL: Required parameter: \'RequestedVersion\' not found - lambda function will not be executed.')
        return
    if not _verify_bg_deployment_type():
        _update_deployment_group()

    _update_deployment_state(event['RequestedVersion'])
    _create_deployment()


# ----------- Logging helpers ------------- #

def _print_info(message):
    print('INFO: {}'.format(message))


def _print_error(message):
    print('ERROR: {}'.format(message))


def _print_warning(message):
    print('WARN: {}'.format(message))


# ------------- boto3 helpers --------------- #

def _get_ssm_client():
    """
    Returns a boto3 ssm client
    :return: client
    """
    return boto3.client('ssm')


def _get_codedeploy_client():
    """
    Returns a boto3 codedeploy client
    :return: client
    """
    return boto3.client('codedeploy')


# --------------------------------------------- #

def _get_herd_asg_name():
    """
    Fetches the current Herd EC2 ASG name
    :return: string. ASG name
    """
    response = _get_ssm_client().get_parameter(
        Name='/app/MDL/{}/{}/HERD/AutoScalingGroup'.format(INSTANCE_NAME, ENVIRONMENT)
    )
    asg_name = response['Parameter']['Value']

    _print_info('Herd ASG name: \'{}\''.format(asg_name))
    return asg_name


def _verify_bg_deployment_type():
    """
    Checks if the deployment-type is BLUE_GREEN or not.
    :return: boolean
    """
    try:
        response = _get_codedeploy_client().get_deployment_group(
            applicationName='{}-Herd'.format(INSTANCE_NAME),
            deploymentGroupName='{}-HerdDeployGroup'.format(INSTANCE_NAME)
        )
        deployment_type = response['deploymentGroupInfo']['deploymentStyle']['deploymentType']

        _print_info('Current deployment-type: \'{}\''.format(deployment_type))
        return deployment_type == 'BLUE_GREEN'

    except ClientError as e:
        _print_error('CodeDeploy caused an exception, {}'.format(e))
        raise Exception('Unknown error: {}'.format(e))


def _update_deployment_group():
    """
    Updates the deployment group's 'deployment type' to 'BLUE_GREEN'
    :return: None
    """
    try:
        _print_info('Attempting to update the deployment style to BLUE_GREEN')
        response = _get_codedeploy_client().update_deployment_group(
            applicationName='{}-Herd'.format(INSTANCE_NAME),
            currentDeploymentGroupName='{}-HerdDeployGroup'.format(INSTANCE_NAME),
            autoScalingGroups=[
                _get_herd_asg_name()
            ],
            loadBalancerInfo={
                'targetGroupInfoList': [
                    {'name': '{}-HerdTargetGroup'.format(INSTANCE_NAME)},
                ]
            },
            deploymentStyle={
                'deploymentType': 'BLUE_GREEN',
                'deploymentOption': 'WITH_TRAFFIC_CONTROL'
            },
            blueGreenDeploymentConfiguration={
                'terminateBlueInstancesOnDeploymentSuccess': {
                    'action': 'TERMINATE'
                },
                'deploymentReadyOption': {
                    'actionOnTimeout': 'CONTINUE_DEPLOYMENT'
                },
                'greenFleetProvisioningOption': {
                    'action': 'COPY_AUTO_SCALING_GROUP'
                }
            },
        )
        status = response.get('ResponseMetadata', {}).get('HTTPStatusCode')

        if status == 200:
            _print_info('Deployment updated to BLUE_GREEN.')
        else:
            _print_error('CodeDeploy returned an error code. status={}'.format(status))
            raise Exception('CodeDeploy \'update-deployment\' error. Response: {}'.format(response))
    except ClientError as e:
        _print_error('CodeDeploy caused an exception, {}'.format(e))
        raise Exception('CodeDeploy \'update-deployment\' error: {}'.format(e))


def _update_deployment_state(requested_version):
    """
    Updates the SSM parameters which hold the deployment 'state'
    :param requested_version: the requested version to upgrade the Herd EC2 stack to.
    :return: None
    """
    try:
        ssm_client = _get_ssm_client()

        _print_info('Updating rolling-deployment condition reference point parameter: \'DeploymentInvoked\'')
        response = ssm_client.put_parameter(
            Name='/app/MDL/{}/{}/HERD/DeploymentInvoked'.format(INSTANCE_NAME, ENVIRONMENT),
            Description='Herd rolling-deployment condition reference.',
            Value='true',
            Type='String',
            Overwrite=True,
        )
        status = response.get('ResponseMetadata', {}).get('HTTPStatusCode')

        if status == 200:
            _print_info('SSM parameter updated.')
        else:
            _print_error('SSM returned an error code. status={}'.format(status))
            raise Exception('Unknown error. Response: {}'.format(response))

        _print_info('Updating requested version parameter: \'RequestedVersion\'')
        response = ssm_client.put_parameter(
            Name='/app/MDL/{}/{}/HERD/RequestedVersion'.format(INSTANCE_NAME, ENVIRONMENT),
            Description='Desired Herd-version for a rolling-deployment upgrade.',
            Value=requested_version,
            Type='String',
            Overwrite=True,
        )

        status = response.get('ResponseMetadata', {}).get('HTTPStatusCode')

        if status == 200:
            _print_info('SSM parameter updated.')
        else:
            _print_error('SSM returned an error code. status={}'.format(status))
            raise Exception('Unknown error. Response: {}'.format(response))

    except ClientError as e:
        _print_error('SSM caused an exception, {}'.format(e))
        raise Exception('SSM error: {}'.format(e))


def _create_deployment():
    """
    Triggers a new Blue/Green deployment with the requested Herd version as previously configured.
    :return: None
    """
    try:
        response = _get_codedeploy_client().create_deployment(
            applicationName='{}-Herd'.format(INSTANCE_NAME),
            deploymentGroupName='{}-HerdDeployGroup'.format(INSTANCE_NAME),
            revision={
                'revisionType': 'S3',
                's3Location': {
                    'bucket': STAGING_BUCKET,
                    'key': '{}/herd/herd.zip'.format(RELEASE_VERSION),
                    'bundleType': 'zip'
                }
            },
            description='Blue/Green deployment triggered by upgrade-herd lambda function',
            ignoreApplicationStopFailures=True,
            autoRollbackConfiguration={
                'enabled': True,
                'events': [
                    'DEPLOYMENT_FAILURE',
                ]
            }
        )
        status = response.get('ResponseMetadata', {}).get('HTTPStatusCode')

        if status == 200:
            _print_info('CodeDeploy Blue/Green upgrade initiated.')
        else:
            _print_error('CodeDeploy returned an error code. status={}'.format(status))
            raise Exception('CodeDeploy \'create-deployment\' error. Response: {}'.format(response))

    except ClientError as e:
        _print_error('CodeDeploy caused an exception, {}'.format(e))
        raise Exception('CodeDeploy \'create-deployment\' failure. Exception: {}'.format(e))
