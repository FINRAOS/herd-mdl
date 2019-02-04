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

INSERT INTO cnfgn (cnfgn_key_nm, cnfgn_value_ds, cnfgn_value_cl) VALUES ('herd.notification.business.object.data.status.change.message.definitions', NULL, '');

update cnfgn
set    cnfgn_value_cl =
E'<?xml version="1.1" encoding="UTF-8"?>
<notificationMessageDefinitions>
  <notificationMessageDefinition>
    <messageType>SNS</messageType>
    <messageDestination>{{SNS_TOPIC_ARN}}</messageDestination>
    <messageVelocityTemplate><![CDATA[{
  "eventDate" : "$current_time",
  "businessObjectDataKey" : {
    "namespace" : "$businessObjectDataKey.namespace",
    "businessObjectDefinitionName" : "$businessObjectDataKey.businessObjectDefinitionName",
    "businessObjectFormatUsage" : "$businessObjectDataKey.businessObjectFormatUsage",
    "businessObjectFormatFileType" : "$businessObjectDataKey.businessObjectFormatFileType",
    "businessObjectFormatVersion" : $businessObjectDataKey.businessObjectFormatVersion,
    "partitionValue" : "$businessObjectDataKey.partitionValue",
    #if($CollectionUtils.isNotEmpty($businessObjectDataKey.subPartitionValues))
    "subPartitionValues" : [
         "$businessObjectDataKey.subPartitionValues.get(0)"
         #foreach ($subPartitionValue in $businessObjectDataKey.subPartitionValues.subList(1, $businessObjectDataKey.subPartitionValues.size())),
         "$subPartitionValue"
         #end
      ],
    #end
    "businessObjectDataVersion" : $businessObjectDataKey.businessObjectDataVersion
  },
  "newBusinessObjectDataStatus" : "$newBusinessObjectDataStatus"
   #if($StringUtils.isNotEmpty($oldBusinessObjectDataStatus)),
      "oldBusinessObjectDataStatus" : "$oldBusinessObjectDataStatus"
   #end
   #if($CollectionUtils.isNotEmpty($businessObjectDataAttributes.keySet())),
      "attributes" : {
   #set ($keys = $Collections.list($Collections.enumeration($businessObjectDataAttributes.keySet())))
      "$keys.get(0)" : "$!businessObjectDataAttributes.get($keys.get(0))"
   #foreach($key in $keys.subList(1, $keys.size()))
   ,   "$key" : "$!businessObjectDataAttributes.get($key)"
   #end
  }
   #end
}
]]></messageVelocityTemplate>
    <messageHeaderDefinitions>
       <messageHeaderDefinition>
          <key>environment</key>
          <valueVelocityTemplate>{{ENVIRONMENT}}</valueVelocityTemplate>
       </messageHeaderDefinition>
       <messageHeaderDefinition>
          <key>userId</key>
          <valueVelocityTemplate>$username</valueVelocityTemplate>
       </messageHeaderDefinition>
       <messageHeaderDefinition>
          <key>sourceSystem</key>
          <valueVelocityTemplate>MDL</valueVelocityTemplate>
       </messageHeaderDefinition>
       <messageHeaderDefinition>
          <key>messageVersion</key>
          <valueVelocityTemplate>1</valueVelocityTemplate>
       </messageHeaderDefinition>
       <messageHeaderDefinition>
          <key>messageType</key>
          <valueVelocityTemplate>BUS_OBJCT_DATA_STTS_CHG</valueVelocityTemplate>
       </messageHeaderDefinition>
       <messageHeaderDefinition>
          <key>messageId</key>
          <valueVelocityTemplate>$uuid</valueVelocityTemplate>
       </messageHeaderDefinition>
       <messageHeaderDefinition>
          <key>namespace</key>
          <valueVelocityTemplate>$namespace</valueVelocityTemplate>
       </messageHeaderDefinition>
    </messageHeaderDefinitions>
  </notificationMessageDefinition>
</notificationMessageDefinitions>'
where  cnfgn_key_nm = 'herd.notification.business.object.data.status.change.message.definitions';
