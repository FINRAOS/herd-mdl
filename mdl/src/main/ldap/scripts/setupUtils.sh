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

# Create utils-file
if [ ! -f ~/utils.sh ]
then
    touch ~/utils.sh
fi

cat >> ~/utils.sh << EOF
#!/bin/bash

# Check the error and fail if the last command is unsuccessful
function check_error() {
    return_code=\${1}
    cmd="\${2}"
    if [ \${return_code} -ne 0 ]
    then
        error "\${cmd} has failed with error \${return_code}"
        exit 1
    fi
}

# Execute the given command and support resume option
function execute_cmd() {
    cmd="\${1}"
    if [[ \${cmd} = *"SecureString"* ]]; then
      inf "Adding/updating SecureString parameter to SSM."
    else
      inf "executing command: \${cmd}"
    fi
    eval \${cmd}
    check_error \${PIPESTATUS[0]} "\${cmd}"
}

EOF

# append to bash_profile to initialize utility functions
echo 'source ~/utils.sh' >> ~/.bash_profile