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
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.sel
-- See the License for the specific language governing permissions and
-- limitations under the License.

DELETE FROM cnfgn WHERE cnfgn_key_nm = 'security.http.header.enabled';
INSERT INTO cnfgn (cnfgn_key_nm, cnfgn_value_ds, cnfgn_value_cl) VALUES ('security.http.header.enabled', 'true', NULL);
INSERT INTO cnfgn (cnfgn_key_nm, cnfgn_value_ds, cnfgn_value_cl) VALUES ('security.http.header.names', 'useridHeader=userprincipalname|firstNameHeader=firstname|lastNameHeader=lastname|emailHeader=email|rolesHeader=memberOf|sessionInitTimeHeader=session-init-time', NULL);
INSERT INTO cnfgn (cnfgn_key_nm, cnfgn_value_ds, cnfgn_value_cl) VALUES ('security.http.header.role.regex.group', 'role', NULL);
INSERT INTO cnfgn (cnfgn_key_nm, cnfgn_value_ds, cnfgn_value_cl) VALUES ('security.http.header.role.regex', 'cn=(?<role>.+?)(,|$)', NULL);
INSERT INTO cnfgn (cnfgn_key_nm, cnfgn_value_ds, cnfgn_value_cl) VALUES ('user.namespace.authorization.enabled', 'true', NULL);
