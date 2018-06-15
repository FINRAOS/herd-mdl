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

# Create logging-file
if [ ! -f ~/logging.sh ];
then
    touch ~/logging.sh
fi

cat >> ~/logging.sh << EOF
#!/bin/bash

# Initializes a basic logger
exec 3>&1 # logging stream defaults to STDOUT
verbosity=3 # default to show 'info' level logging events
silent_lvl=0
crt_lvl=1
err_lvl=2
inf_lvl=3
wrn_lvl=4
dbg_lvl=5

notify() { log \${silent_lvl} "NOTE: \${1}"; }  # Always prints
critical() { log \${crt_lvl} "CRITICAL: \${1}"; }
error() { log \${err_lvl} "ERROR: \${1}"; }
warn() { log \${wrn_lvl} "WARNING: \${1}"; }
inf() { log \${inf_lvl} "INFO: \${1}"; } # "info" is already a command
debug() { log \${dbg_lvl} "DEBUG: \${1}"; }
log() {
    if [ \${verbosity} -ge \$1 ]; then
        datestring=\`date +'%Y-%m-%d %H:%M:%S'\`
        # Expand escaped characters, wrap at 70 chars
        echo -e "\${datestring} \${2}" | fold -w70 -s >&3
    fi
}
EOF

# append to bash_profile to initialize logging functions
echo 'source ~/logging.sh' >> ~/.bash_profile