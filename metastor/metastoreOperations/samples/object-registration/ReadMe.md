# Object Registration
## Overview
Utility to easily register new objects with Herd and back load registered available partitions to Herd-Metastore. 
This script register following notifications:
+ Data Notification - To get notifications about partitions availability with Herd
+ Storage unit notification - To get notification about any storage related changes like archiving or restoring objects with Herd

### Pre-requisites
Update 'registerBusinessObjects.sh' EMR cluster and Metastore DB related variables

### How to Run
+ Create a new object registration request listing one or more objects similar to the information mentioned in METASTORE-sample.txt
+ Run the 'registerBusinessObjects.sh' with input as
	1. Object registration request file name
	2. Herd Rest URL
	3. Herd Credential file path

### License
Herd is licensed under [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)


