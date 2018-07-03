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

insert IGNORE into DM_NOTIFICATION (ID, NAMESPACE,OBJECT_DEF_NAME,USAGE_CODE,FILE_TYPE,WF_TYPE,EXECUTION_ID)
                     VALUES (99, 'NS', 'OD', 'U', 'FT', '0', 'EID');

INSERT INTO METASTOR_PROCESSING_LOG (NOTIFICATION_ID,DATE_PROCESSED)
    VALUES (99, TIMESTAMPADD(MINUTE,-2, now())),
           (99, TIMESTAMPADD(SECOND, -60, now()));