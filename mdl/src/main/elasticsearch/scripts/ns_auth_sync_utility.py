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
import json
import os

import boto3
import requests
from botocore.exceptions import ClientError
from impala.dbapi import connect

# event fields
ACTION = 'action'
FULL_SYNC = 'full_sync'
DELTA_SYNC = 'delta_sync'

ALLOWED_ACTIONS = {FULL_SYNC, DELTA_SYNC}

# CloudFormation response
SUCCESS = "SUCCESS"
FAILED = "FAILED"

USER = 'userId'
PUBLIC_READ_USER = '*'
NAMESPACE = 'namespace'

# global variables to hold MDL stack information
INSTANCE_NAME = os.environ['INSTANCE_NAME']
ENVIRONMENT = os.environ['ENVIRONMENT']
BDSQL_MASTER = ''

# global variables to hold Herd information
HERD_REST_PROTOCOL = 'https'
ADMIN_USERNAME = None
ADMIN_PASS = None
HERD_HEADERS = None
HERD_BASE_URL = None
HERD_REST_BASE_PATH = None

S3_HOME_BUCKET = None

# Log debug statements toggle
DEBUG_ENABLED = os.environ['DEBUG_ENABLED']


def handler(event, context):
    """
    Function to manage SQL Standard Based Hive Authorization on MDL's Presto cluster.
    Reads user-namespaces permissions from Herd and translates them into Hive authorizations thus
    achieving a fully-functional RBAC with one single source-of-truth. This function can be triggered in the following
    two ways:
    1. full_sync: Reads all user-namespace permissions from Herd and performs grants on Hive.
    2. delta_sync: Reads only a single user-namespace permission and performs grants/revokes on Hive.
    Please note that this will only grant the SELECT privilege on 'business' schema objects to normal users.
    It will also create a new 'user-schema' for each user and grant them ownership to it.
    Either of the above mentioned processes can be started by invoking this function and sending in an event object
    as below:

    event = {
                "action": "fullsync|delta_sync",
                "userId": "<username>", [only needed for delta_sync]
                "namespace": "<namespace>", [only needed for delta_sync]
            }


    :param event: incoming lambda event
    :param context: incoming context
    :return: None
    """
    if event['Records'][0]['Sns']['Message'] is None:
        _print_info('Unrecognized event, function will not be executed. Enable debug to log the actual event.')
        _print_debug('event: {}'.format(event))
        return

    message = event['Records'][0]['Sns']['Message']
    _print_debug('message received: {}'.format(message))

    event = json.loads(message)
    _print_info('event: {}'.format(json.dumps(event)))

    if event[ACTION] in ALLOWED_ACTIONS:

        _print_info('Requested action: {}'.format(event[ACTION]))

        _print_info('Initializing.')
        _init_vars_()

        # create a hive cursor which can be passed around and then closed when done.
        cursor = _create_hive_cursor()

        if event[ACTION] == FULL_SYNC:
            _sync_all(cursor)
        if event[ACTION] == DELTA_SYNC:
            if event[USER] and event[NAMESPACE]:
                _sync_delta(cursor, event[USER], event[NAMESPACE])
            else:
                _print_error(
                    'Invalid request. Expecting both: a valid \'{}\' and a valid \'{}\''.format(
                        USER, NAMESPACE))

        # close the hive cursor when done
        _close_hive_cursor(cursor)
    else:
        _print_error(
            'Unknown action. Expecting one of: \'{}\', \'{}\''.format(FULL_SYNC,
                                                                      DELTA_SYNC))


# ---------------- drivers ---------------- #
def _sync_all(cursor):
    """
    Syncs all namespace permissions with bdsql privileges
    :return: None
    """
    _print_info('Syncing all privileges.')

    all_namespace_permissions = _fetch_all_namespace_permissions(cursor)

    for namespace_permission in all_namespace_permissions:
        namespace = namespace_permission['namespace']
        users = namespace_permission['users']

        _print_info('Working on namespace: \'{}\''.format(namespace))
        for user in users:
            _grant_select_privilege(cursor, user, namespace)


def _sync_delta(cursor, user, namespace):
    """
    Syncs a single change in user-namespace permissions with bdsql
    :param user: the given user
    :param namespace: the given namespace
    :return: None
    """

    schemas = _fetch_hive_schemas(cursor, namespace.lower())
    if namespace.lower() not in schemas:
        _print_info('No corresponding schema for namespace: \'{}\'. Skipping.'.format(namespace))

    # sync all for wildcard user
    if user == PUBLIC_READ_USER:
        _print_debug('Syncing all for public read user')
        _sync_all(cursor)

    has_permission = _has_read_permission_user_namespace(user, namespace)

    # create user schema if does not already exist
    _create_user_schema(cursor, user)

    if has_permission:
        _grant_select_privilege(cursor, user, namespace)
    else:
        _revoke_select_privilege(cursor, user, namespace)


# ------------- Logging helpers --------------- #

def _print_info(message):
    print('INFO: {}'.format(message))


def _print_error(message):
    print('ERROR: {}'.format(message))


def _print_warning(message):
    print('WARN: {}'.format(message))


def _print_debug(message):
    if DEBUG_ENABLED == 'yes':
        print('DEBUG: {}'.format(message))


# -------- Initialize global variables -------- #

def _init_vars_():
    """
    Populates global variables with values from SSM/Environment/declared constants.
    :return: None
    """

    global ADMIN_USERNAME
    ADMIN_USERNAME = _fetch_ssm_parameter(
        '/app/MDL/{}/{}/LDAP/User/HerdAdminUsername'
            .format(INSTANCE_NAME, ENVIRONMENT), False)

    global ADMIN_PASS
    ADMIN_PASS = _fetch_ssm_parameter(
        '/app/MDL/{}/{}/LDAP/Password/HerdAdminPassword'.format(INSTANCE_NAME,
                                                                ENVIRONMENT),
        True)

    global HERD_REST_BASE_PATH
    HERD_REST_BASE_PATH = 'herd-app/rest'

    global HERD_BASE_URL
    HERD_BASE_URL = '{}-herd.dev.aws.cloudfjord.com'.format(INSTANCE_NAME)

    global HERD_HEADERS
    HERD_HEADERS = {
        'Accept': 'application/json',
        'Content-type': 'application/json'
    }

    global S3_HOME_BUCKET
    S3_HOME_BUCKET = _fetch_ssm_parameter('/app/MDL/{}/{}/S3/MDL'
                                          .format(INSTANCE_NAME, ENVIRONMENT),
                                          False)

    global BDSQL_MASTER
    BDSQL_MASTER = _fetch_ssm_parameter('/app/MDL/{}/{}/Bdsql/MasterIp'
                                        .format(INSTANCE_NAME, ENVIRONMENT),
                                        False)


def _create_hive_cursor():
    """
    Initializes a hive connection and returns a cursor to it
    :return: hive cursor
    """
    _print_info('Initializing hive cursor.')
    return _initialize_hive_connection()


def _close_hive_cursor(cursor):
    """
    Closes the cursor to an open hive connection
    :param cursor: the given cursor
    :return: None
    """
    _print_info('Closing hive cursor.')
    cursor.close()


# ---------- Data-access helpers ----------- #

def _initialize_hive_connection(database='default'):
    """
    Initializes a hive connection
    :param database: the specified database to initialize a hive connection with
    :return: a cursor to the hive server2 connection
    """
    _print_info('Connecting to hive on host: \'{}:10000\', user: \'{}\' '
                'and database: \'{}\''.format(BDSQL_MASTER, ADMIN_USERNAME,
                                              database))
    try:
        hive_connection = connect(host=BDSQL_MASTER,
                                  port=10000,
                                  user=ADMIN_USERNAME,
                                  password=ADMIN_PASS,
                                  auth_mechanism='PLAIN',
                                  database=database)
        return hive_connection.cursor()
    except Exception, err:
        _print_error('Could not create a connection to hive: {}'.format(err))


def _run_hive_query(cursor, query, has_results=False):
    """
    Executes a given hive query on a specified database
    :param cursor: cursor to run queries on
    :param query: the specified hive query to run
    :return: results
    """
    _print_debug('Executing query: {}'.format(query))
    cursor.execute(query)

    if has_results:
        rows = cursor.fetchall()
        _print_debug('Query returned {} rows'.format(len(rows)))
        return rows


def _fetch_herd_session():
    """
    Returns a session to communicate with Herd
    :return:
    """
    session = requests.Session()
    session.auth = (ADMIN_USERNAME, ADMIN_PASS)
    session.headers.update(HERD_HEADERS)

    return session


# ---------------- AWS Helpers ----------------- #

def _fetch_ssm_parameter(key_name, with_decryption=False):
    """
    Fetches a parameter from SSM with the specified name.
    :param key_name: parameter key
    :return: parameter's value
    """
    try:
        ssm = boto3.client('ssm')
        response = ssm.get_parameter(
            Name=key_name,
            WithDecryption=with_decryption
        )
        if response['ResponseMetadata']['HTTPStatusCode'] == 200:
            _print_debug(
                'Found parameter with key name: \'{}\' in SSM.'.format(
                    key_name))
            return response['Parameter']['Value']

    except ClientError as e:
        if e.response['ResponseMetadata']['HTTPStatusCode'] == 400:
            _print_error(
                'Parameter with key: \'{}\' not found in SSM.'.format(key_name))
        else:
            _print_error(
                'Unexpected error while trying to get parameter: \'{}\'. Exception: {}'.format(
                    key_name, e))


# ---------------- Herd helpers ----------------- #

def _fetch_all_namespaces():
    """
    Fetches all namespaces registered with Herd.
    :return: list of all namespace 'names'
    """
    response = _fetch_herd_session() \
        .get('{}://{}/{}/{}'.format(HERD_REST_PROTOCOL, HERD_BASE_URL,
                                    HERD_REST_BASE_PATH, 'namespaces')) \
        .json()

    namespaces = []
    for namespaceKey in response['namespaceKeys']:
        namespaces.append(namespaceKey['namespaceCode'])

    _print_info('Retrieved {} namespaces.'.format(len(namespaces)))
    return namespaces


def _fetch_all_namespace_permissions(cursor):
    """
    Fetches all user-namespace-permissions mapping registered with Herd
    :param: cursor to run hive queries
    :return: list of all users that have READ on each respective namespace
    """
    namespaces = _fetch_all_namespaces()

    user_namespace_permissions = []
    all_users = set()
    for namespace in namespaces:
        _print_info('Fetching namespace permissions for namespace: {}'.format(
            namespace))
        response = _fetch_herd_session() \
            .get('{}://{}/{}/{}'.format(HERD_REST_PROTOCOL, HERD_BASE_URL,
                                        HERD_REST_BASE_PATH,
                                        '/userNamespaceAuthorizations/namespaces/{}').format(
            namespace)) \
            .json()

        public_read = False

        namespace_users = []
        for authorization in response['userNamespaceAuthorizations']:
            if 'READ' in authorization['namespacePermissions']:
                namespace_users.append(
                    authorization['userNamespaceAuthorizationKey']['userId'])

                # add each user to the global users set
                all_users.add(
                    authorization['userNamespaceAuthorizationKey']['userId'])

                # check if read-all is enabled on namespace
                if authorization['userNamespaceAuthorizationKey']['userId'] == PUBLIC_READ_USER:
                    public_read = True

        _print_info(
            'Found {} users with READ permissions on namespace: {}'.format(
                len(namespace_users), namespace))
        user_namespace_permissions.append({
            'namespace': namespace,
            'users': namespace_users
        })

        # grant read to all users if read-all is enabled, otherwise - revoke
        _print_info(
            'Public read option enabled on namespace: \'{}\'? {}'.format(
                namespace, public_read))
        _manage_public_read(cursor, namespace, public_read)

    # manage user-schemas for all users
    for user in all_users:
        _create_user_schema(cursor, user)

    return user_namespace_permissions


def _has_read_permission_user_namespace(user, namespace):
    """
    Checks if a given user has READ permission on a given namespace
    :param user: the specified user
    :param namespace: the specified namespace
    :return: boolean
    """
    response = _fetch_herd_session() \
        .get('{}://{}/{}/{}'.format(HERD_REST_PROTOCOL, HERD_BASE_URL,
                                    HERD_REST_BASE_PATH,
                                    '/userNamespaceAuthorizations/userIds/{}/namespaces/{}').format(
        user, namespace))

    if response.status_code == 200:
        json_response = response.json()
        return 'READ' in json_response['namespacePermissions']
    else:
        return False


def _user_has_select_privilege_on_table(cursor, user, schema, table):
    """
    Checks if a given user has SELECT privilege on a given table
    :param cursor: hive cursor to run queries
    :param user: username
    :param schema: the schema the given table belongs to
    :param table: the given table
    :return: boolean
    """

    # ignore wildcard user
    if user == PUBLIC_READ_USER:
        return

    schema = schema.lower()
    cursor.execute('set role admin')
    grants = _run_hive_query(cursor,
                             'SHOW GRANT USER {} on TABLE {}.{}'.format(user,
                                                                        schema,
                                                                        table),
                             True)

    if (len(grants)) == 0:
        return False

    for grant in grants:
        if 'SELECT' == grant[6]:
            return True
    return False


def _role_has_select_privilege_on_table(cursor, role, schema, table):
    """
    Checks if a given role has SELECT privilege on a given table
    :param cursor: hive cursor to run queries
    :param role: role name
    :param schema: the schema the given table belongs to
    :param table: the given table
    :return: boolean
    """

    schema = schema.lower()

    cursor.execute('set role admin')
    cursor.execute('USE {}'.format(schema))
    grants = _run_hive_query(cursor,
                             'SHOW GRANT ROLE {} on TABLE {}'.format(role,
                                                                     table),
                             True)

    if (len(grants)) == 0:
        return False

    for grant in grants:
        if 'SELECT' == grant[6]:
            return True
    return False


# ---------------- Hive helpers ---------------- #

def _create_user_schema(cursor, username):
    """
    Creates a user schema (if not exists already)
    :param cursor: hive cursor to run queries
    :param username: the specified user
    :return: None
    """
    # ignore wildcard user
    if username == PUBLIC_READ_USER:
        _print_info(
            'Will not create a schema for wildcard user: \'{}\'.'.format(
                username))
        return

    username = username.lower()
    user_schema_name = 'user_{}'.format(username)
    user_schema_location = 's3://{}/BDSQL/home/{}.db'.format(
        S3_HOME_BUCKET, user_schema_name)

    _print_info(
        'Attempting to create schema (if it does not already exist) for user=\'{}\' user_schema=\'{}\' '
        's3_location=\'{}\''.format(username, user_schema_name,
                                    user_schema_location))

    # fetch matching schemas and check if a user schema already exists
    matching_schemas = _fetch_hive_schemas(cursor, user_schema_name)

    if user_schema_name not in matching_schemas:

        # hive query to create user schema
        create_schema_query = "CREATE SCHEMA IF NOT EXISTS {} LOCATION '{}'".format(
            user_schema_name, user_schema_location)

        # hive query to grant ownership of a given user schema to user
        alter_schema_user_query = 'ALTER DATABASE {} SET OWNER USER {}'.format(
            user_schema_name, username)

        # run queries as admin user
        cursor.execute('set role admin')

        # create the user schema
        _run_hive_query(cursor, create_schema_query, False)
        _print_info('Created new schema: \'{}\''.format(user_schema_name))

        # grant ownership on the user schema to the user
        _run_hive_query(cursor, alter_schema_user_query, False)
        _print_info('Granted ownership on schema \'{}\' to user \'{}\''.format(
            user_schema_name, username))

    else:
        _print_info('Schema \'{}\' already exists - nothing to do.'.format(
            user_schema_name))


def _fetch_hive_schemas(cursor, matcher):
    """
    Fetches schemas matching a specific suffix
    :param cursor: hive cursor to run queries
    :param matcher: suffix
    :return: list of all schemas that match: %suffix
    """
    results = _run_hive_query(cursor,
                              'SHOW SCHEMAS LIKE \'*{}*\''.format(matcher),
                              has_results=True)
    schemas = []
    for schema in results:
        schemas.append(schema[0])

    return schemas


# ----------------------------------------------- #

def _grant_select_privilege(cursor, user, schema):
    """
    Grants SELECT privilege on all tables to a given user
    :param cursor: hive cursor to run queries
    :param user: the specified user
    :param schema: the specified schema
    :return: None
    """
    # ignore public read user
    if user == PUBLIC_READ_USER:
        return

    schema = schema.lower()

    # run queries as admin user
    cursor.execute('set role admin')
    tables = _run_hive_query(cursor, 'SHOW TABLES IN {}'.format(schema), True)

    # grant select privilege if user does not already have it
    for table in tables:
        if not _user_has_select_privilege_on_table(cursor, user, schema,
                                                   table[0]):
            _print_info(
                'Granting SELECT privilege to user: \'{}\' on table: \'{}.{}\''.format(
                    user, schema, table[0]))
            cursor.execute(
                'GRANT SELECT on {}.{} TO USER {}'.format(schema, table[0],
                                                          user))
        else:
            _print_info(
                'User: \'{}\' already has SELECT privilege on table: \'{}.{}\'. Nothing to do.'
                    .format(user, schema, table[0]))


def _revoke_select_privilege(cursor, user, schema):
    """
    Grants SELECT privilege on all tables to a given user
    :param cursor: hive cursor to run queries
    :param user: the specified user
    :param schema: the specified schema
    :return: None
    """
    # ignore public read user
    if user == PUBLIC_READ_USER:
        return

    schema = schema.lower()

    _print_info(
        'Revoking SELECT from user: \'{}\' on all tables of schema: \'{}\''.format(
            user, schema))
    # run queries as admin user
    cursor.execute('set role admin')
    tables = _run_hive_query(cursor, 'SHOW TABLES IN {}'.format(schema), True)

    # revoke select privilege from user
    for table in tables:
        if _user_has_select_privilege_on_table(cursor, user, schema, table[0]):
            cursor.execute(
                'REVOKE SELECT on {}.{} FROM USER {}'.format(schema, table[0],
                                                             user))


def _manage_public_read(cursor, schema, public_read_option):
    """
    Grant/revoke public read on a given schema
    :type public_read_option: boolean
    """
    schemas = _fetch_hive_schemas(cursor, schema.lower())
    if schema.lower() not in schemas:
        _print_info('No corresponding schema for namespace: \'{}\'. Skipping.'.format(schema))
        return

    if public_read_option:
        _print_info(
            'Granting public read on all tables of schema: \'{}\''.format(
                schema))
    else:
        _print_info(
            'Revoking public read from all tables of schema: \'{}\''.format(
                schema))

    schema = schema.lower()

    # run queries as admin user
    cursor.execute('set role admin')
    tables = _run_hive_query(cursor, 'SHOW TABLES IN {}'.format(schema), True)
    cursor.execute('USE {}'.format(schema))

    for table in tables:
        if public_read_option:
            if not _role_has_select_privilege_on_table(cursor, 'public', schema,
                                                       table[0]):
                cursor.execute(
                    'GRANT SELECT on {}.{} TO ROLE public'.format(schema,
                                                                  table[0]))
            else:
                _print_debug('Table already has public read - nothing to do.')
        else:
            if _role_has_select_privilege_on_table(cursor, 'public', schema,
                                                   table[0]):
                cursor.execute(
                    'REVOKE SELECT on {}.{} FROM ROLE public'.format(schema,
                                                                     table[0]))
            else:
                _print_debug('Table does not have public read - nothing to do.')


# ------------------------------------------------- #
