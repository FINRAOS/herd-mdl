-- Copyright 2018 herd-mdl contributors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- sequences
\set scrty_role_fn_seq 'scrty_role_fn_seq'
\set scrty_role_seq 'scrty_role_seq'

-- Insert the security role
INSERT INTO scrty_role(scrty_role_cd, scrty_role_ds, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (:scrty_role, 'security role with only read permission', current_timestamp, 'system', current_timestamp, 'system');

-- Insert the security role to function mappings
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_ALLOWED_ATTRIBUTE_VALUES_ALL_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_ATTRIBUTE_VALUE_LISTS_DELETE', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_ATTRIBUTE_VALUE_LISTS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_ATTRIBUTE_VALUE_LISTS_GET_ALL', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_ATTRIBUTE_VALUE_LISTS_POST', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUILD_INFO_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_ATTRIBUTES_ALL_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_ATTRIBUTES_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_AVAILABILITY_COLLECTION_POST', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_AVAILABILITY_POST', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_BY_BUSINESS_OBJECT_DEFINITION_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_BY_BUSINESS_OBJECT_FORMAT_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_GENERATE_DDL_COLLECTION_POST', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_GENERATE_DDL_POST', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_NOTIFICATION_REGISTRATIONS_BY_NAMESPACE_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_NOTIFICATION_REGISTRATIONS_BY_NOTIFICATION_FILTER_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_NOTIFICATION_REGISTRATIONS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_S3_KEY_PREFIX_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_SEARCH_POST', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_STATUS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_DATA_VERSIONS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_BUSINESS_OBJECT_FORMATS_GENERATE_DDL_COLLECTION_POST', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_CUSTOM_DDLS_ALL_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_CUSTOM_DDLS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_DATA_PROVIDERS_ALL_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_DATA_PROVIDERS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_DOWNLOAD_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_EMR_CLUSTERS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_EMR_CLUSTER_DEFINITIONS_ALL_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_EMR_CLUSTER_DEFINITIONS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_EXPECTED_PARTITION_VALUES_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_FILE_TYPES_ALL_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_GLOBAL_ATTRIBUTE_DEFINITIONS_ALL_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_GLOBAL_ATTRIBUTE_DEFINITIONS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_JOBS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_JOBS_GET_BY_ID', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_JOB_DEFINITIONS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_PARTITION_KEY_GROUPS_ALL_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_PARTITION_KEY_GROUPS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_STORAGES_ALL_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_STORAGES_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_STORAGE_PLATFORMS_ALL_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_STORAGE_PLATFORMS_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_STORAGE_POLICIES_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_STORAGE_UNIT_NOTIFICATION_REGISTRATIONS_BY_NAMESPACE_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_STORAGE_UNIT_NOTIFICATION_REGISTRATIONS_BY_NOTIFICATION_FILTER_GET', current_timestamp, 'system', current_timestamp, 'system');
INSERT INTO dmrowner.scrty_role_fn (scrty_role_fn_id, scrty_role_cd, scrty_fn_cd, creat_ts, creat_user_id, updt_ts, updt_user_id) VALUES (nextval(:scrty_role_fn_seq), :scrty_role, 'FN_STORAGE_UNIT_NOTIFICATION_REGISTRATIONS_GET', current_timestamp, 'system', current_timestamp, 'system');
