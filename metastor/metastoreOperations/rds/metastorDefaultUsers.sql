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
CREATE SCHEMA IF NOT EXISTS `metastor`;

-- Create DB Users
CREATE USER 'MS_Hive_0_13'@'%' IDENTIFIED BY '{{HIVE_PASSWORD}}';
CREATE USER 'MS_Presto'@'%' IDENTIFIED BY '{{HIVE_PASSWORD}}';

-- Grants priviledges
GRANT ALL PRIVILEGES ON metastor.* TO 'MS_Hive_0_13'@'%' REQUIRE SSL;
GRANT ALL PRIVILEGES ON metastor.* TO 'MS_Presto'@'%' REQUIRE SSL;
