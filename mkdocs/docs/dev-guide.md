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

1\. Clone the herd-mdl repo from GitHub  
  `git clone https://github.com/FINRAOS/herd-mdl.git && cd herd-mdl`
  
2\. Switch to the branch you want to deploy  
  `git checkout <branch-name>`
  
3\. Pull our official docker image from docker-hub   
  `docker pull finraos/herd-mdl`
  
4\. Navigate to the directory with build scripts    
  `cd mdl/build/`
  
  This step is needed because the _build-script_ needs a parameter-file which has default values for
  CloudFormation when bringing up a stack, and this directory has one which you can modify and use.
  
  Verify that the folder-structure looks like this: 
  
```
.
├── Dockerfile
├── build_and_deploy.py
├── default-parameters.json
└── env.list
```

5\. Make changes to the `env.list` file according to your needs, this file gets passed to the docker container as an env-file  
 

```
action=deploy              
build_from=remote          
branch=develop
s3_bucket=s3_bucket_name
s3_bucket_prefix=prefix
default_stack_name=demo-stack
proxy=<proxy-host>          
parameter_file_name=default-parameters.json  
custom_tags=[{'Key': 'CustomKey','Value': 'CustomValue'}]      
local_repo_path=/path/to/your/repo
remote_repo_path=https://github.com/<username>/herd-mdl.git
show_output=True          

```

#### Parameters

| **Parameter name** | **Required** | **Possible value(s)** | **Description** |
| ----- | ----- | ----- | ----- |
| action | Y |build <br> deploy | Builds the artifact and upload to S3. <br> Builds, uploads to S3 and launches a new stack.
| build_from | Y |remote <br><br><br> local | Clones remote repository to the container, switches to the specified branch and uses it to build artifacts. <br> Uses your local repository (needs to be mounted to the docker container)
| branch | N | <git branch\> | The Git branch you want to build artifacts from.
| s3_bucket | N | <s3bucket\>| Name of the S3 bucket you want to upload the build-artifacts to.
| s3_bucket_prefix | N | <prefix\> | An optional prefix to use when uploading the build-artifact.
| default_stack_name | Y | <CloudFormation stack-name\> | The name to use when launching the MDL stack. Please note that there shouldn't be an existing stack with this name or the deployment will fail.
| proxy | N | <proxyhost:port\> | An optional proxy used by the boto3 client to connect to AWS.
| parameter_file_name | Y | default_parameters_file.json | Name of the parameters file which is used to populate when launching the CloudFormation stack. This could be any valid JSON file with the same format as the one included in the herd-mdl repository [link]
| custom_tags | N | json_array | An optional set of tags you want applied to your CloudFormation stack.
| local_repo_path | N | /path/to/your/local/repo | fully-qualified path to your local repo. Please note that this directory also needs to be mounted on the docker container as a bind-mount.
| remote_repo_path | N | https://remote/repo/location.git | Remote repository location eg: `https://github.com/<username>/herd-mdl.git`
| show_output | N | True <br> False | Show output from system commands <br> Suppress system command outputs

6\. Prepare to launch your docker container

```
docker create --name <container-name> \
              --env-file env.list \
              --mount="--mount src="<local_repo_path>",target="<local_repo_path>",type=bind " \  
              --env AWS_DEFAULT_REGION=<aws_region> \
              --env AWS_ACCESS_KEY_ID=<aws_access_key_id> \
              --env AWS_SECRET_ACCESS_KEY=<aws_secret_access_key> \
              --env AWS_SESSION_TOKEN=<aws_session_token> \
              <image-name>
```

> Note: The `mount` option is not required if you're building/deploying from a remote Git location.

7\. Copy the parameters file to the container

`docker cp default-parameters.json <container-name>:/herd-mdl/default-parameters.json`

8\. Launch the container 

`docker start --interactive <container-name>`

> Note: The default _entrypoint_ of this docker image runs the build-script. If you want to access to shell: override
  the entrypoint:  
  `docker start -it <container-name> --entrypoint /bin/bash`

<br>

### Running the build-script

If you would like to run the _build-and-deploy_ script as a standalone and not use our docker image- please 
follow the instructions below.

**Prerequisites**

* JDK 8  
[Download](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* Maven 3.5.4  
[Download](https://maven.apache.org/download.cgi)
* Python 3  
[Download](https://www.python.org/getit/)
* Install python dependencies
    - [GitPython](https://github.com/gitpython-developers/GitPython)
    - [boto3](https://github.com/boto/boto3)

**Steps**

1\. Provide the required/optional parameters to the script as environment variables as needed. Refer to the list of accepted parameters [here](#parameters). 

> Examples
>
> * Linux/OSX: `export action=deploy`
> * Windows: `set action=deploy`
  
2\. Run the python script.  
`python3 build_and_deploy.py`

3\. Verify that the stack was launched from your AWS console.


### Notes

1\. You don't need to clone herd-mdl if you're only building it from a remote git location.  
2\. The build script runs some system commands which could generate a lot of output, change the env-var: `show_output` to `False` to suppress output.  



