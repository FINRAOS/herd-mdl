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
#!/bin/bash
testFileNameWithExtension=$1

cd /home/ec2-user
if [ "${EnableSSLAndAuth}" == "true" ] ; then
    httpPrefix=https
else
    httpPrefix=http
fi

ldapAppUsername=$(aws ssm get-parameter --name "/mdl/ldap/app_user" --region ${RegionName} --output text --query Parameter.Value)
ldapAppPassword=$(aws ssm get-parameter --name  "/mdl/ldap/app_pass" --with-decryption --region ${RegionName} --output text --query Parameter.Value)

sed -i "s/{{httpPrefix}}/${httpPrefix}/g" ./mdlt/scripts/html/${testFileNameWithExtension}
sed -i "s/{{ldapAppUsername}}/${ldapAppUsername}/g" ./mdlt/scripts/html/${testFileNameWithExtension}
sed -i "s/{{ldapAppPwd}}/${ldapAppPassword}/g" ./mdlt/scripts/html/${testFileNameWithExtension}
aws s3 cp ./mdlt/scripts/html/${testFileNameWithExtension} s3://${ShepherdS3Bucket}