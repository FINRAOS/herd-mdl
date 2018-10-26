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

INSERT INTO cnfgn (cnfgn_key_nm, cnfgn_value_ds, cnfgn_value_cl) VALUES ('herd.notification.user.namespace.authorization.change.message.definitions', NULL, '');

update cnfgn
set    cnfgn_value_cl =
E'<?xml version="1.1" encoding="UTF-8"?>
<notificationMessageDefinitions>
   <notificationMessageDefinition>
      <messageType>SNS</messageType>
      <messageDestination>{{SNS_TOPIC_ARN}}</messageDestination>
      <messageHeaderDefinitions>
         <messageHeaderDefinition>
            <key>environment</key>
            <valueVelocityTemplate>{{ENVIRONMENT}}</valueVelocityTemplate>
         </messageHeaderDefinition>
         <messageHeaderDefinition>
            <key>messageType</key>
            <valueVelocityTemplate>USER_NAMESPACE_ATHRN_CHG</valueVelocityTemplate>
         </messageHeaderDefinition>
         <messageHeaderDefinition>
            <key>messageVersion</key>
            <valueVelocityTemplate>1</valueVelocityTemplate>
         </messageHeaderDefinition>
         <messageHeaderDefinition>
            <key>sourceSystem</key>
            <valueVelocityTemplate>HERD</valueVelocityTemplate>
         </messageHeaderDefinition>
         <messageHeaderDefinition>
            <key>messageId</key>
            <valueVelocityTemplate>$uuid</valueVelocityTemplate>
         </messageHeaderDefinition>
         <messageHeaderDefinition>
            <key>userId</key>
            <valueVelocityTemplate>$username</valueVelocityTemplate>
         </messageHeaderDefinition>
         <messageHeaderDefinition>
            <key>namespace</key>
            <valueVelocityTemplate>$namespace</valueVelocityTemplate>
         </messageHeaderDefinition>
      </messageHeaderDefinitions>
      <messageVelocityTemplate><![CDATA[{
        "action" : "delta_sync",
        "userId" : "$userNamespaceAuthorizationKey.userId",
        "namespace" : "$userNamespaceAuthorizationKey.namespace"
  }]]></messageVelocityTemplate>
   </notificationMessageDefinition>
</notificationMessageDefinitions>'
where  cnfgn_key_nm = 'herd.notification.user.namespace.authorization.change.message.definitions';
