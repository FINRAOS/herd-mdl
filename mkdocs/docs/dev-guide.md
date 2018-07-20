Build and deploy instructions
=============================

## How to build and deploy an MDL stack

We _dockerized_ our build-and-deploy environment to keep it simple and save you the hassle of installing
 JDK, Maven and Python on your machine. With that being said, if you'd like to use your own development 
environment- please skip the steps below and read the instructions [here](#running-the-build-script).

#### Prerequisites

* S3-bucket to upload herd-mdl artifacts.
* Valid AWS Credentials (for the build script to be able to upload to S3)
* Docker installed on your machine

#### Steps

* Clone the herd-mdl repo from GitHub 
  `git clone https://github.com/FINRAOS/herd-mdl.git && cd herd-mdl`
  
* Switch to the branch you want to deploy  
  `git checkout <branch-name>`
  
* Pull our official docker image from docker-hub   
  `docker pull finraos/herd-mdl`
  
* Navigate to the directory with build scripts    
  `cd herd-mdl/mdl/build/`
  > 
  This step is needed because the _build-script_ needs a parameter-file which has default values for
  CloudFormation when bringing up a stack, and this directory has one which you can modify and use.
  
  the folder-structure looks like this: 
```
.
├── assembly
├── src
├── target
└── pom.xml
```

* Make changes to the `env.list` file according to your needs, this file gets passed to the docker container as an env-file

```
action=deploy               ## build|deploy
build_from=remote           ## remote|local
branch=develop
s3_bucket=herd-mdl-deploy-bucket
s3_bucket_prefix=sid
default_stack_name=demo-stack
proxy=<proxy-host>          ## optional
parameter_file_name=default-parameters.json  
custom_tags=[{'Key': 'CustomKey','Value': 'CustomValue'}]      ## optional
local_repo_path=/path/to/your/repo
remote_repo_path=https://github.com/<username>/herd-mdl.git
show_output=True            ## True|False

```

**Parameters**

|   |   |   |
| ----- | ----- | ----- |
| **Parameter name** | **Possible value(s)** | **Description** |
| action | build <br> deploy | Builds the artifact and upload to S3. <br> Builds, uploads to S3 and launches a new stack.
| build_from | remote <br><br> local | Clones remote repository to the container, switches to the specified branch and uses it to build artifacts. <br> Uses your local repository (needs to be mounted to the docker container)
| branch | <git branch\> | The Git branch you want to build artifacts from.
| s3_bucket | <s3bucket\>| Name of the S3 bucket you want to upload the build-artifacts to.
| s3_bucket_prefix | <prefix\> | An optional prefix to use when uploading the build-artifact.
| default_stack_name | <CloudFormation stack-name\> | The name to use when launching the MDL stack. Please note that there shouldn't be an existing stack with this name or deployment will fail.
| proxy | <proxyhost:port\> | An optional proxy used by the boto3 client to connect to AWS.
| parameter_file_name | default_parameters_file.json | Name of the parameters file which is used to populate when launching the CloudFormation stack. This could be any valid JSON file with the same format as the one included in the herd-mdl repository [link]
| custom_tags | json_array | An optional set of tags you want applied to your CloudFormation stack.
| local_repo_path | /path/to/your/local/repo | fully-qualified path to your local repo. Please note that this directory also needs to be mounted on the docker container as a bind-mount.
| remote_repo_path | https://remote/repo/location.git | Remote repository location eg: `https://github.com/<username>/herd-mdl.git`
| show_output | True <br> False | Show output from system commands <br> Suppress system command outputs

#### Running the build-script

