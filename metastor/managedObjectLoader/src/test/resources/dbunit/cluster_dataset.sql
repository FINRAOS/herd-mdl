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

INSERT INTO DM_NOTIFICATION
(`NAMESPACE`,
 `OBJECT_DEF_NAME`,
 `USAGE_CODE`,
 `FILE_TYPE`,
 `PARTITION_VALUES`,
 `WF_TYPE`,
 `EXECUTION_ID`, DATE_CREATED)
VALUES
  ('A','A','A','A','1','0','0',now()),
  ('A','B','A','A','1','0','1',now()),
  ('A','A','A','A','1','0','2','2016-09-12 13:01:00');
