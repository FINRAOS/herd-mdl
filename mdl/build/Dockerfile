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

# Build on base-image which has jdk8, maven3.5.4 and python3
FROM openkbs/jdk-mvn-py3

MAINTAINER herd_mdl@finra.org

WORKDIR /herd-mdl

# Add script file
COPY build_and_deploy.py .

# Upgrade pip and install python dependencies
RUN pip3 install --upgrade pip
RUN pip3 install boto3
RUN pip3 install GitPython

# default entrypoint to the container
ENTRYPOINT ["python3", "build_and_deploy.py"]
