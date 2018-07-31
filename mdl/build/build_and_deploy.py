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

import ast
import glob
import json
import logging
import os
import platform
import subprocess
import sys

import boto3
from botocore.config import Config
from git import Repo

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s %(name)-5s %(levelname)-8s %(message)s',
                    datefmt='%m-%d %H:%M')

# default to show all outputs
show_output = 'True'
originating_path = ''
local_repo_path = ''

actions = ['build', 'deploy']


def main():
    """
    entry-point into the build and deploy script.
    :return: None
    """

    # verify that all required env vars exist
    _verify_env_variables()

    # save root path for the interpreter to run in the correct location
    global originating_path
    originating_path = os.getcwd()

    # determine if running system commands should display output
    global show_output
    show_output = os.getenv('show_output')
    outputs = ''
    if show_output == 'False':
        outputs = 'not '
    logging.info('\'show_outputs\' is set to {}, command outputs will {}be displayed'.format(show_output, outputs))

    # determine if user wants to build from a local git repository or if they want to pull from remote
    if os.getenv('build_from') == 'remote':
        _pull_from_remote()
    elif os.getenv('build_from') == 'local':
        global local_repo_path
        local_repo_path = os.getenv('local_repo_path')

        logging.info('using local git repository at: {}'.format(local_repo_path))
        _verify_branch()

    # run maven goal which builds the zip artifact
    _build_deploy_artifact()

    if os.getenv('action') in actions:
        # clean bucket from the prefix
        _clean_bucket()

        # upload artifact to S3
        _upload_artifact()

    if os.getenv('action') == 'deploy':
        # verify that a stack does not exist already with the given name
        _verify_stack()

        # verify template and deploy a new stack
        _deploy_stack()


def _verify_env_variables():
    """
    Verifies that required env variable(s) exist and valid.
    :return: None
    """
    if os.getenv('remote_repo_path') is None and os.getenv('local_repo_path') is None:
        complain('One of: remote_repo_path or local_repo_path is required. Aborting.')

    if os.getenv('action') not in actions:
        complain('\'action\' should be one of: \'build\', \'deploy\'')

    required_vars = ['action', 'branch', 'build_from', 's3_bucket', 's3_bucket_prefix', 'default_stack_name']
    missing_vars = []

    for var in required_vars:
        if os.getenv(var) is None:
            missing_vars.append(var)
    if len(missing_vars) > 0:
        complain('Required env variable(s): {} not found. Aborting.'.format(missing_vars))


def complain(error_msg):
    """
    Aborts execution and exists with the supplied error message
    :param error_msg: the specified error message
    :return: None
    """
    logging.error(error_msg)
    sys.exit(1)


def _pull_from_remote():
    """
    Performs a 'git clone' on the repository as specified
    :return: None
    """
    logging.info('pulling from remote git repository: {}'.format(os.getenv('remote_repo_path')))
    _run_process('git clone {}'.format(os.getenv('remote_repo_path')))
    os.chdir('{}'.format('herd-mdl'))
    _run_process('git checkout {}'.format(os.getenv('branch')))

    global local_repo_path
    local_repo_path = os.getcwd()


def _verify_branch():
    """
    Verifies that the current branch on the repo is what the user is trying to build from.
    We don't want to automatically switch to the requested branch in case the user has
    uncommitted changes.
    :return: None
    """
    logging.info('verifying repo branch.')

    repo = Repo(local_repo_path)
    actual_branch = str(repo.active_branch)
    expected_branch = str(os.getenv('branch'))

    if actual_branch != expected_branch:
        raise ValueError('You\'re on an unexpected branch, will abort. Expected: {}, actual: {}'.format(expected_branch,
                                                                                                        actual_branch))


def _run_process(command):
    """
    Runs a given command and returns output line by line
    :param command:
    :return:
    """

    logging.info('running command: \'{}\''.format(command))
    args = command.split()
    if 'Windows' == platform.system():
        p = subprocess.Popen(args,
                             stdout=subprocess.PIPE,
                             stderr=subprocess.STDOUT,
                             shell=True)

    else:
        p = subprocess.Popen(args,
                             stdout=subprocess.PIPE,
                             stderr=subprocess.STDOUT)

    for line in iter(p.stdout.readline, b''):
        if show_output == 'True':
            logging.info(line.decode('utf-8'))
    logging.info('Finished running command.')


def _build_deploy_artifact():
    """
    performs a maven build of the
    :return:
    """
    logging.info('performing a maven build.')

    os.chdir('{}/{}'.format(local_repo_path, 'mdl'))
    _run_process('mvn clean install -e -DskipTests')


def get_s3_client(use_proxy):
    if use_proxy:
        proxy = os.getenv('proxy')
        logging.info('proxy used for s3: {}'.format(proxy))
        return boto3.resource('s3', config=Config(proxies={'https': proxy}))
    else:
        return boto3.resource('s3')


def _get_template_path():
    os.chdir(local_repo_path)

    template = glob.glob('**/installMDL.yml', recursive=True)[0]
    return os.path.abspath(template)


def _replace_template(template_path):
    """
    replace the artifact url in the installMDL.yml
    :return: template_body
    """
    logging.info('replace template: {}'.format(template_path))
    with open(template_path) as template_fileobj:
        template_body = template_fileobj.read()

    s3_artifacts_location_url = _get_artifact_url(os.getenv('s3_bucket'), os.getenv('s3_bucket_prefix'))
    git_hub_artifacts_url = 'https://github.com/FINRAOS/herd-mdl/releases/download/mdl-v{releaseVersion}/herd-mdl-{' \
                            'releaseVersion}-dist.zip'
    template_body = template_body.replace(git_hub_artifacts_url, s3_artifacts_location_url)

    return template_body


def _upload_artifact():

    os.chdir('{}/{}/{}'.format(local_repo_path, 'mdl', 'target'))

    artifact = glob.glob('herd-mdl-*.zip', recursive=True)[0]
    logging.info('artifact name: {}'.format(artifact))
    s3 = get_s3_client(os.getenv('proxy').strip())

    # upload artifact and give it public access
    logging.info('uploading artifact to S3. Bucket: {}, Prefix: {}'.format(os.getenv('s3_bucket'),
                                                                           os.getenv('s3_bucket_prefix')))
    data = open('./{}'.format(artifact), 'rb')
    s3.Bucket(os.getenv('s3_bucket')) \
        .put_object(ACL='public-read',
                    Key='{}/{}'.format(os.getenv('s3_bucket_prefix'), artifact),
                    Body=data,
                    ServerSideEncryption='AES256')

    # upload installMDL.yml to s3
    template_body = _replace_template(template_path=_get_template_path())
    s3.Bucket(os.getenv('s3_bucket')) \
        .put_object(ACL='public-read',
                    Key='{}/{}'.format(os.getenv('s3_bucket_prefix'), 'installMDL.yml'),
                    Body=template_body,
                    ServerSideEncryption='AES256')


def _verify_stack():
    """
    Verifies that a stack does not already exist with the specified default stack name
    :return: None
    """
    client = boto3.client('cloudformation')
    response = client.list_stacks(
        StackStatusFilter=[
            'CREATE_IN_PROGRESS',
            'CREATE_COMPLETE',
            'UPDATE_COMPLETE'
        ]
    )
    for stack in response['StackSummaries']:
        if stack['StackName'] is os.getenv('default_stack_name'):
            raise ValueError('Stack: {} already exists, will abort.'.format(stack['StackName']))


def _validate_template(template_path):
    """
    Verifies that a given template is valid
    :return: None
    """
    logging.info('validating template: {}'.format(template_path))
    template_body = _replace_template(template_path)

    client = boto3.client('cloudformation')
    client.validate_template(TemplateBody=template_body)

    return template_body


def _load_cft_overrides():
    """
    loads CFT override parameters from the default-parameters config file
    :return: list of parameter-value pairs
    """
    os.chdir(originating_path)
    with open(os.getenv('parameter_file_name')) as params_file:
        data = json.load(params_file)
        return data['Parameters']


def _clean_bucket():
    """
       Deletes all objects from the specified prefix.
       :return: None
       """
    s3 = boto3.client('s3')
    bucket_name = os.getenv('s3_bucket')
    prefix = os.getenv('s3_bucket_prefix')

    try:
        bucket = boto3.resource('s3').Bucket(bucket_name)
        size = sum(1 for _ in bucket.objects.all())
        print('Found {} objects.'.format(size))

        while size is not 0:
            objects_to_delete = []
            for key in s3.list_objects_v2(Bucket=bucket_name, Prefix=prefix, MaxKeys=1000)['Contents']:
                objects_to_delete.append({
                    'Key': key['Key']
                })

            logging.info('Deleting {} objects from bucket: {}'.format(len(objects_to_delete), bucket_name))
            s3.delete_objects(
                Bucket=bucket_name,
                Delete={
                    'Objects': objects_to_delete
                }
            )
            size = sum(1 for _ in bucket.objects.all())

    except Exception as ex:
        logging.error('deletion failed: {}'.format(ex))


def _get_artifact_url(bucket_name, prefix):
    """
    Gets the uploaded artifact S3 URL
    :param bucket_name: s3 bucket-name as specified
    :param prefix: s3 prefix to the object
    :return: String: S3 URL of the artifact
    """
    s3 = get_s3_client(os.getenv('proxy').strip())

    bucket = s3.Bucket(bucket_name)
    objects_sum = bucket.objects.filter(Prefix=prefix)
    for obj_sum in objects_sum:
        if obj_sum.key.endswith('.zip'):
            logging.info('artifact key: {}'.format(obj_sum.key))
            return 'https://s3.amazonaws.com/{}/{}'.format(bucket_name, obj_sum.key)


def _deploy_stack():
    """
    Deploys stack with the template as specified in the config file
    :return: None
    """

    # validate template before deploying
    template_body = _validate_template(_get_template_path())

    parameters = _load_cft_overrides()
    custom_tags = ast.literal_eval(os.getenv('custom_tags'))

    client = boto3.client('cloudformation')
    stack = client.create_stack(
        StackName=os.getenv('default_stack_name'),
        TemplateBody=template_body,
        Parameters=parameters,
        DisableRollback=True,
        Capabilities=[
            'CAPABILITY_NAMED_IAM',
        ],
        Tags=custom_tags
    )
    if [stack['ResponseMetadata']['HTTPStatusCode'] is 200]:
        logging.info('Stack is being deployed, id: {}'.format(stack['StackId']))


main()
