
Herd-MDL User Guide
==============================

## Overview

Let's get to know Herd-MDL by exploring these features:

*   Search and Discovery - use Herd-UI to view the demo objects that are created during Herd installation – this is how end users will locate and understand data in Herd-MDL
*   Interactive Query - connect to Big Data SQL (BDSQL) JDBC endpoint to query the data as it sits in S3 - this is how end users will access the data
*   Ingestion - explore two ways to ingest data into the Herd MDL - the Uploader tool and direct S3 upload with API registration
*   Integration - call some Herd REST endpoints that are used by Herd-UI and discuss various other integrations using the REST APIs.

## Search and Discovery in Herd-UI

**Purpose - search and browse in Herd-UI and view the detailed business and technical metadata that is stored in the Herd catalog.**

Terminology

*   Data Entity (also known as a Business Object Definition in Herd APIs) - represents a set of data with a specific business meaning e.g. Trade Details. Includes descriptive information and details of data format.
*   Data Object (also known as a Business Object Data in Herd APIs) - represents actual data in some storage location. Includes information about physical location, partitions, and storage platform.
*   Category (also known as Tag in Herd APIs) - represents a business concept that is used to group and describe Data Entities, e.g. Market Data

### Activities

Pre-requisites - Herd-MDL is installed and validated; User has browser and network access to Herd-UI URL.

Browse the catalog

*   Locate the Herd-UI URL in output of Herd-MDL wrapper stack and enter that URL into a web browser
*   Browse by clicking 'SEC' on the Herd-UI home page.
*   Only a single category (SEC) is created in the demo install.
*   Herd administrators and/or data publishers can define more categories and tag Data Entities with categories to build a browse-able taxonomy.
*   The Category page has a description of the category and a list of Data Entities that have been tagged with this category
*   The list shows a short description of each Data Entity along with its display name and physical name.
*   Users can filter the list of Data Entities by clicking on checkboxes to the left. This list includes all categories of all Data Entities in the list.

Search the catalog

*   On the Home page, enter a term (such as 'Price') in the search box and press the search button (magnifying glass icon).
*   The Search Results page shows a list of all Data Entities and Categories that contain that term
*   The term matches are visible with hit highlighting in the 'Found In' section of each search result. This shows how the term matched in the name, description, column name or description, etc in the Data Entity
*   Users can filter the list of Data Entities by clicking on checkboxes to the left. This list includes all categories of all Data Entities in the search results.
*   Click on a Data Entity name (like 'Security Test Object') to view more details about that Data Entity

Learn about a Data Entity

*   The Data Entity page for each object has a description, a list of categories it's been tagged with, and a list of contacts that are experts on this data.
*   The 'Columns' tab lists all columns in the data entity (if it's columnar data) along with description for each column and its datatype
*   View Data Objects registered for this Data Entity by clicking 'Data Object List'

View Data Objects

*   The Data Object List page shows all the Data Objects that have been registered for this Data Entity along with their partition values and information about their format
*   Click on the 'View Data Object' link for any of the Data Objects to see more details
*   The Data Object Detail page shows all details about a data object including its partition values, audit information such as when it was registered, and its storage type and physical location

More information

*   Data publishers and/or Herd administrators can manage all the descriptive information and tagging via on-screen editing or via APIs as described in [Populating Business Metadata](https://github.com/FINRAOS/herd/wiki/Populating-Business-Metadata) on the Herd GitHub wiki
*   More details about each page/screen in Herd-UI are available in the [Herd-UI User Guide](https://github.com/FINRAOS/herd-ui/wiki/User-Guide) in the Herd-UI GitHub wiki

### Take-away

_Herd-UI is a powerful search and discovery tool that allows end users to rapidly locate and understand data across the breadth of a massive data lake._

## Interactive Query with BDSQL

**Purpose - explore data anywhere in the data lake using Big Data SQL (BDSQL), a Presto query cluster that can access all data registered in the Herd catalog**

Terminology

*   Data Entity (also known as a Business Object Definition in Herd APIs) - represents a set of data with a specific business meaning e.g. Trade Details. Includes descriptive information and details of data format.

### Activities

Pre-requisites - Herd-MDL is installed and validated; User has SQL client with presto-jdbc-0.190 driver and network access to the BDSQL JDBC endpoint

This section will use the 'Security Test Object'. The Data Entity page for 'Security Test Object' is <your Herd-UI hostame>/data-entities/SEC\_MARKET\_DATA/SecurityData

Query data

*   Locate the BDSQL JDBC connection string in the output of the Herd-MDL wrapper stack and create a JDBC connection with a SQL client.
*   For demo installs without authentication, the user is 'hadoop' and the password is left blank.
*   It's easy to query data from any Data Entity in the data lake. Simply locate some basic technical metadata on the Data Entity page of the Herd-UI
*   The technical metadata required is: Namespace, Physical Name, Usage, and File Type. For example for the 'Security Test Object', these values are: Namespace=SEC\_MARKET\_DATA, Physical Name=SecurityData, Usage=MDL, and File Type=TXT
*   Query the data in the SQL client – the schema and logical table name for JDBC query are made up of Namespace.PhysicalName\_Usage\_FileType,
*   So to query 'Security Test Object', enter ```'select * from sec\_market\_data.securitydata\_mdl\_txt'.```
*   The SQL client will display the results. Use partitions in the where clause to optimize performance.

More information

*   More details about how physical metadata in Herd is used to enable BDSQL is available in the [Technical Overview](http://127.0.0.1:8000/docs/tech-overview/#herd-mdl-architecture)
*   The instance type and node count of the BDSQL Presto cluster can be tuned for performance

### Take-aways

_Users can query anything in the data lake in seconds. The example above has one object – but Herd-MDL achieves interactive query performance even if you have thousands of Data entities, millions of Data Objects and partitions, and cases where each Data Object has tens of billions of rows that make up a Data Entity with trillions of rows._

_BDSQL exposes all the data in your data lake to any user who can perform SQL queries. It looks like one gigantic database accessible to anyone with a SQL client – but it's actually based on more scalable and cost-effective technology than even the most expensive proprietary big data appliances._

## Ingesting data into Herd-MDL

**Purpose - provide an overview and simplified example of how to register data in Herd**

Terminology

*   Data Entity (also known as a Business Object Definition in Herd APIs) - represents a set of data with a specific business meaning e.g. Trade Details. Includes descriptive information and details of data format.
*   Data Object (also known as a Business Object Data in Herd APIs) - represents actual data in some storage location. Includes information about physical location, partitions, and storage platform.

### Activities

Pre-requisites

*   Herd-MDL is installed and validated; User has machine with access to S3 endpoints to upload the data and Herd endpoints to register the data
*   Download [herd_reg_demo.zip](../img/herd_reg_demo.zip) and unzip in a directory
*   Install Postman REST client
*   Obtain the Herd Uploader jar - use the link for "HerdUploaderJarURL" in the output of your Herd-MDL CloudFormation stack

Perform one-time steps to register a new object (Business Object Definition with Format) in the Herd metadata catalog

1.  Import all 3 json files from herd_reg_demo/register_one_time into Postman. These files contain import the following:
    1.  Collection called 'MDL Demo' that contains all requests that we will execute in sequence to perform the registration.
    1.  Postman Environment called 'MDL Demo' that contains the Herd hostname. This is referenced
from each request in the collection. To edit, click the gear icon in the upper-right that says 'Manage Environment'. Then click on 'MDL Demo' from the list to view the variable. Replace the domain value with the domain from the 'HerdURL' entry in the output of your Herd-MDL CloudFormation stack.
    1.  Postman Global Variables for Namespace and BO Definition Name. These are referenced from request and can be changed to
run this demo multiple times without colliding with previously created objects.
1.  Select 'MDL Demo' environment from the environment drop-down in the upper-right hand corner of Postman.
1.  To test connectivity, run the 'Current User' request in the collection. For demo installations without authentication should receive results for TRUSTED_USER. 
1.  Now, run the following requests in sequence to register a new object in the catalog:
    1.  Create Namespace - creates a new Namespace for your object. The value of the Namespace comes from the Postman Global
Variable you imported which by default is 'reg_demo'
    1.  Create BO Definition - creates a new Business Object Definition for your object. The value of the BO Definition Name comes
from the Postman Global Variable you imported which by default is 'demo_data'
    1.  Create BO Format - creates a Format for your object. The Format is simple - it has only two columns and is partitioned by
'Transaction Date'
    1.  Descriptive PUT - adds a friendly display name and definition
    1.  Register for Metastore-BDSQL - this registers the new object for inclusion in the Metastore used by the BDSQL interative query
cluster.
1.  Go to Herd UI and search for 'demo_data' (or whatever you might have named your BO Definition). You will see a result for the object
you just created. Click to see the Data Entity page for your object.
1.  Drill down using the 'Data Object List' link and you will see that there are not yet any BData partitions registered

Register a new partition (Business Object Data) in the Herd metadata catalog. This step is generally performed in an ongoing fashion with new
partitions/BData as they arrive.

1.  Get to a command line and navigate to the herd_reg_demo/register_bdata_ongoing directory
1.  Open command.bat and replace the hostname value in the '-H' argument with the he domain from the 'HerdURL' entry in the output of your Herd-MDL CloudFormation stack
1.  Place the Herd Uploader jar in the herd_reg_demo/register_bdata_ongoing directory
1.  Run command.bat - it performs the following:
    1.  command.bat triggers the Herd uploader tool with command line arguments referring to the Herd API host, the data file
associated with this BData, and a manifest file that contains metadata about this BData
    1.  command.bat also contains additional command line arguments as documented in Uploader Users Guide
    1.  Note that you can configure a proxy if needed to access Herd and/or S3 using arguments documented in the Users Guide
1.  Uploader tool performs the following:
    1.  Read manifest file and confirm it references a valid BO Definition, Format, and Partition value for the BData being updated
    1.  NOTE – unlike the Postman Collection that has variables for Namespace and BO Definition Name, the manifest.json has these
values hard-coded. If you've changed either of those variables when registering with Postman, you must alter these values in the manifest.
json as well.
    1.  Call Herd API to pre-register the partition based on metadata from the manifest file and obtain S3 location for data upload
    1.  Call Herd API to get temporary credentials to upload data file to S3. This step requires the user has WRITE permissions to the
Namespace
    1.  Upload the file to S3 using the temporary credentials
    1.  Call Herd API to confirm the data is in place and mark the new BData Partition VALID. This designates it is ready for consumers
to access the data.
1.  Return to Herd UI and refresh the Data Object List page. The newly-registered BData will now appear. Drill down to see details such as
S3 location, audit information.
1.  After a delay (30-minutes if Metastore cluster is idle, <10 minutes if Metastore cluster was running), the new data will be available to
select in BDSQL interactive query

More information

*   Learn more about the Herd catalog in the [Herd Wiki](https://github.com/FINRAOS/herd/wiki/quick-start-to-registering-data) and about Herd REST APIs by browsing the [Herd Swagger API documentation](https://finraos.github.io/herd/docs/latest/rest/index.html)

### Take-aways

_There is a learning curve but after that, registering data is fairly straightforward. The registration process at FINRA is highly automated.
Some use cases automate use of the Uploader tool. Other use cases perform some variation of the steps the the Uploader tool performs
but they all ultimately place data in S3 and call Herd APIs to register the data. Other use cases include:  integration between Herd and FINRA's ETL framework; integrations with custom apps of different varieties_

_This example shows registration of columnar data consumed through BDSQL but a similar approach is used to register document-based
data for consumption by other tools._

_The Uploader tool is a quick, easy way to ingest Data Objects into the Herd-MDL. This tool has been used extensively at FINRA. Many teams script the generation of manifest files and the uploader CLI. Petabytes of data have been uploaded using this tool._

## How to find MDL User Credentials to login to Herd/Shepherd/Bdsql

This section describes how to locate credentials required for endpoints when you have installed with EnableSSLAndAuth=true.
> Note: A detailed description and a list of all default users and auth groups created for your stack can be found in the [manage OpenLdap section](#managing-openldap-users-and-groups)

**Prerequisites**

*   AWS Console Access of the AWS Account, where MDL is created
*   MDL Instance Name of the MDL stack
    *   This is the parameter to the MDL Cloudformation Stack
*   EnableSSLAndAuth must be set to true while creating the stack

**Steps**

*   Login to AWS Console and navigate to SSM Parameter section (Refer [AWS Documentation](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-console.html))
*   User Name
    *   Find the parameter: /app/MDL/${MDLInstanceName}/${Environment}/LDAP/User/HerdAdminUsername on the console
        *   Example :  `/app/MDL/mdlstack/dev/LDAP/User/HerdAdminUsername`
    *   Get the Value for the above parameter. That value specifies the user name for Herd/Bdsql/Shepherd
        *   Example: `herd_admin_user`
*   Password  
    *   Find the parameter: /app/MDL/${MDLInstanceName}/${Environment}/Password/HerdAdminPassword on the console
        *   Example :  `/app/MDL/mdlstack/dev/LDAP/Password/HerdAdminPassword`
    *   Get the Value for the above parameter, it is a 12-letter AlphaNumeric String which specifies the password for Herd/Bdsql/Shepherd, and it is a Secure String
        *   Example: `ODMyOTdmZmE5`
*   Use the above User name, and Password to login to Herd/Shepherd/Bdsql  

## Herd-MDL additional use cases

*   Scale!
    *   Herd-MDL is proven at scale. FINRA uses the Herd catalog, Herd-UI, and BDSQL for interactive query in a data lake with multiple petabytes. It's all the same technology as presented in this User Guide -- just at a much larger scale
*   Integrations
    *   Teams at FINRA have integrated with Herd-MDL to fulfill various data processing and analytics requirements. Contact us through GitHub for questions or a demonstration.
    *   Ingest / Processing
        *   Document-based use case - custom apps for data intake from end users
        *   Batch-processing use case - ETL framework that registers data and establishes lineage
    *   Analytics
        *   Big Data BI tools - either custom or COTS like Tableau or Pentaho
        *   Data Science tool including native Spark library for Herd
