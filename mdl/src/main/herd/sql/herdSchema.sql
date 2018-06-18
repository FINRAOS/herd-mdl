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
CREATE ROLE {{HERD_DB_USER}} LOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION;
ALTER ROLE {{HERD_DB_USER}} SET search_path = {{HERD_DB_USER}};
ALTER user {{HERD_DB_USER}} WITH PASSWORD '{{HERD_DB_USER_PASSWORD}}';

CREATE SCHEMA {{HERD_DB_USER}};
ALTER SCHEMA {{HERD_DB_USER}} OWNER TO {{HERD_DB_USER}};