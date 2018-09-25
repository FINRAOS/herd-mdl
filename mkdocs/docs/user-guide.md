
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

**Purpose - demonstrate two methods of loading and registering data into Herd-MDL**

Terminology

*   Data Entity (also known as a Business Object Definition in Herd APIs) - represents a set of data with a specific business meaning e.g. Trade Details. Includes descriptive information and details of data format.
*   Data Object (also known as a Business Object Data in Herd APIs) - represents actual data in some storage location. Includes information about physical location, partitions, and storage platform.

### Activities

Pre-requisites

*   Herd-MDL is installed and validated; User has machine with access to S3 endpoints to upload the data and Herd endpoints to register the data
*   Obtain the Herd Uploader jar - use the link for "HerdUploaderJarURL" in the output of your Herd-MDL CloudFormation stack
*   Obtain the data files for [2017-08-10](https://github.com/FINRAOS/herd-mdl/blob/master/mdl/src/main/metastorEc2/data/2017-08-10/2017-08-10.security-data.txt) and [2017-08-11](https://github.com/FINRAOS/herd-mdl/blob/master/mdl/src/main/metastorEc2/data/2017-08-11/2017-08-11.security-data.txt)

View the currently registered Data Objects for the 'Security Test Object' at <your Herd-UI hostame>/data-objects/SEC\_MARKET\_DATA/SecurityData. The Data Object List shows date-partitioned data from 2018-08-01 through 2018-08-09

Load data using the Uploader Tool, an ingestion utility created by the Herd team.

*   The Uploader populates your data lake by putting data in S3 and registering the data with the Herd catalog.
*   Place the uploader jar in a directory and the data file in a subdirectory called 'data'
*   The Uploader tool requires a JSON manifest that contains the metadata for registration in the Herd catalog. Here is the manifest file for the 2018-08-10 SecurityData registration:

```
{
  "namespace": "SEC_MARKET_DATA",
  "businessObjectDefinitionName": "SecurityData",
  "businessObjectFormatUsage": "MDL",
  "businessObjectFormatFileType": "TXT",
  "businessObjectFormatVersion": "0",
  "partitionKey": "TDATE",
  "partitionValue": "2017-08-10",
  "manifestFiles" : [ 
    {
      "fileName" : "2017-08-10.security-data.txt",
      "rowCount" : 504
    }
  ]
}
```

*   Create a manifest file named manifest.json with the contents above in the directory with the jar
*   In that same directory, execute the following command (your uploader version will differ)

```
java -jar herd-uploader-0.71.0.jar -l ./data -m ./manifest.json -V -H {HERD_LOAD_BALANCER_DNS_NAME}
```

*   The uploader tool performs the following steps:
    *   Pre-registers a BData in the Herd catalog with 'Uploading' status and obtains a Herd-configured S3 prefix
    *   Obtains temporary credentials to upload to that S3 prefix. If Herd is configured for Namespace-level authorization, only users with write permissions to the SEC\_MARKET\_DATA namespace can obtain credentials.
    *   Uploads the file from the local filesystem to the S3 prefix obtained in the previous step. The S3 upload AWS multipart uploader with up to 10 parallel threads for performance and with automatic retry for reliability.
    *   Registers the BData as 'VALID' once the S3 upload completes

Load the data using S3 APIs and Herd REST APIs

*   Coming soon, detailed, steps as follows including sample request JSON for Herd
*   Call Herd Pre-Registration API to obtain S3 Prefix
*   Use S3 API to upload file(s) to prefix
*   Call Herd Registration API to validate data and update status

More information

*   The examples above are for loading new Data Objects to an existing Data Entity that was created by the Herd-MDL install automation. Creating a new Data Entity is described in detail in the Herd tutorial [Quick Start to Registering Data](https://github.com/FINRAOS/herd/wiki/quick-start-to-registering-data)
*   Learn more about the Herd catalog in the [Herd Wiki](https://github.com/FINRAOS/herd/wiki/quick-start-to-registering-data) and about Herd REST APIs by browsing the [Herd Swagger API documentation](https://finraos.github.io/herd/docs/latest/rest/index.html)

### Take-aways

_The Uploader tool is a quick, easy way to ingest Data Objects into the Herd-MDL. This tool has been used extensively at FINRA. Many teams script the generation of manifest files and the uploader CLI. Petabytes of data have been uploaded using this tool.._

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
