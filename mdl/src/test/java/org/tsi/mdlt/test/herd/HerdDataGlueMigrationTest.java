/*
 * Copyright 2018 herd-mdl contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
**/
package org.tsi.mdlt.test.herd;

import static org.awaitility.Awaitility.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.glue.model.Column;
import com.amazonaws.services.glue.model.Table;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.s3.transfer.model.UploadResult;
import io.restassured.response.Response;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.finra.herd.model.api.xml.Attribute;
import org.finra.herd.model.api.xml.BusinessObjectData;
import org.finra.herd.model.api.xml.BusinessObjectFormatCreateRequest;
import org.finra.herd.model.api.xml.Schema;
import org.finra.herd.model.api.xml.SchemaColumn;
import org.finra.herd.model.api.xml.StorageUnit;
import org.finra.herd.model.jpa.BusinessObjectDataStatusEntity;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsi.mdlt.aws.AthenaUtils;
import org.tsi.mdlt.aws.GlueUtils;
import org.tsi.mdlt.aws.LambdaUtils;
import org.tsi.mdlt.aws.SsmUtil;
import org.tsi.mdlt.enums.SsmParameterKeyEnum;
import org.tsi.mdlt.enums.StackInputParameterKeyEnum;
import org.tsi.mdlt.test.BaseTest;
import org.tsi.mdlt.util.HerdRestUtil;
import org.tsi.mdlt.util.HerdUploader;
import org.tsi.mdlt.util.TestProperties;

public class HerdDataGlueMigrationTest extends BaseTest {

    private static final Logger LOGGER = LoggerFactory
        .getLogger(MethodHandles.lookup().lookupClass());

    @Test
    public void testHerdNewBzDefGlueMigration() {
        String namespace = "MDL";
        String bzDefName = "MDL_OBJECT";
        BusinessObjectData testBzData = getDefaultBzOjbData(namespace, bzDefName);
        String usage = testBzData.getBusinessObjectFormatUsage();
        String fileType = testBzData.getBusinessObjectFormatFileType();
        int version = testBzData.getBusinessObjectFormatVersion();

        LogVerification("Verify Demo Herd data found in Glue");
        String databaseName = constructGlueDbName(namespace);
        String tableName = constructGlueTableName(bzDefName, fileType, usage, version);
        verifyGluedata(testBzData, databaseName, tableName);

        LogVerification("Verify Demo Herd data found in Athena");
        String instanceName = TestProperties.get(StackInputParameterKeyEnum.MDL_INSTANCE_NAME);
        List<String> colName = Arrays.asList("column1", "column2", "tdate");
        List<String> colValue = Arrays.asList("1", instanceName + "-mdl-test data", "2017-01-02");
        List<Map<String, String>> results = AthenaUtils.getTableTop10(databaseName, tableName);
        assertEquals(3, results.size());
        verifyAthenaData(colName, colValue, results);
    }

    //TODO Athena data is deleted, but Glue metadata still exists, maybe issue to fix in the future
    @Test
    public void testDeleteBzObjData() throws InterruptedException {
        String namespace = NAMESPACE_MDL;
        String bzDefName = "GLUE_TEST_DEF_DEL";
        String dataProvider = "MDL";
        BusinessObjectData demoBzObject = getDefaultBzOjbData(namespace, bzDefName);
        String usage = demoBzObject.getBusinessObjectFormatUsage();
        String fileType = demoBzObject.getBusinessObjectFormatFileType();
        int version = demoBzObject.getBusinessObjectFormatVersion();

        LogStep("Cleanup steps in case testcase failed");
        cleanUpBzObject(demoBzObject);

        LogStep("Create Business Object Data prerequisites");
        HerdRestUtil
            .deleteBusinessObjectData(HERD_ADMIN_USER, demoBzObject);
        HerdRestUtil
            .postBusinessObjectDefinition(HERD_ADMIN_USER, NAMESPACE_MDL, bzDefName, dataProvider);
        HerdRestUtil
            .createBusinessObjectFormat(HERD_ADMIN_USER, getFormatCreateRequest(namespace, bzDefName));

        LogStep("Upload business object data");
        UploadResult uploadResult = HerdUploader.uploadFile(HERD_ADMIN_USER, NAMESPACE_MDL, bzDefName);
        LOGGER.info("business object data uploaded with version :" + uploadResult.getVersionId());

        LogVerification("Verify Herd data found in Glue");
        String databaseName = constructGlueDbName(namespace);
        String tableName = constructGlueTableName(bzDefName, fileType, usage, version);
        verifyGluedata(demoBzObject, databaseName, tableName);

        LogVerification("Verify Herd data found in Athena");
        List<String> colName = Arrays.asList("column1", "column2", "tdate");
        List<String> colValue = Arrays.asList("1", "glue-test data", "2017-01-02");
        List<Map<String, String>> results = AthenaUtils.getTableTop10(databaseName, tableName);
        assertEquals(2, results.size());
        verifyAthenaData(colName, colValue, results);

        LogStep("delete business object data");
        Response response = HerdRestUtil.deleteBusinessObjectData(HERD_ADMIN_USER, demoBzObject);
        assertEquals(HttpStatus.SC_OK, response.statusCode());

        LogVerification("Verify aws glue and athena");
        verifyGluedata(demoBzObject, databaseName, tableName);
        assertEquals(0, AthenaUtils.getTableTop10(databaseName, tableName).size());

        LogCleanup("Cleanup data");
        cleanUpBzObject(demoBzObject);
    }

    @Test
    public void testMarkBzObjDataStatusAsDeleted() throws InterruptedException {
        String namespace = NAMESPACE_MDL;
        String bzDefName = "GLUE_TEST_DEF_MARK_DEL";
        String dataProvider = "MDL";
        BusinessObjectData demoBzObject = getDefaultBzOjbData(namespace, bzDefName);
        String usage = demoBzObject.getBusinessObjectFormatUsage();
        String fileType = demoBzObject.getBusinessObjectFormatFileType();
        int version = demoBzObject.getBusinessObjectFormatVersion();

        LogCleanup("Cleanup data");
        cleanUpBzObject(demoBzObject);

        LogStep("Create Business Object Data prerequisites");
        HerdRestUtil.postBusinessObjectDefinition(HERD_ADMIN_USER, namespace, bzDefName, dataProvider);
        HerdRestUtil
            .createBusinessObjectFormat(HERD_ADMIN_USER, getFormatCreateRequest(namespace, bzDefName));

        LogStep("Upload business object data");
        HerdUploader.uploadFile(HERD_ADMIN_USER, namespace, bzDefName);

        LogStep("Mark business object data status as Deleted");
        Response response = HerdRestUtil.updateBusinessObjectDataStatus(HERD_ADMIN_USER, demoBzObject,
            BusinessObjectDataStatusEntity.DELETED);
        assertEquals(HttpStatus.SC_OK, response.statusCode());

        LogVerification("Verify aws glue and athena");
        String databaseName = constructGlueDbName(namespace);
        String tableName = constructGlueTableName(bzDefName, fileType, usage, version);
        verifyGluedata(demoBzObject, databaseName, tableName);
        assertEquals(0, AthenaUtils.getTableTop10(databaseName, tableName).size());

        LogCleanup("Cleanup data");
        cleanUpBzObject(demoBzObject);
    }

    @Test
    public void testInvalidLambdaParameters() throws InterruptedException, IOException {
        String namespace = NAMESPACE_MDL;
        String bzDefName = "MDL_OBJECT";
        BusinessObjectData demoBzObject = getDefaultBzOjbData(namespace, bzDefName);
        String usage = demoBzObject.getBusinessObjectFormatUsage();
        String fileType = demoBzObject.getBusinessObjectFormatFileType();
        int version = demoBzObject.getBusinessObjectFormatVersion();

        LogVerification("Invoke Glue lambda with invalid namespace");
        String glueLambdaName = SsmUtil
            .getPlainParameter(SsmParameterKeyEnum.GLUE_SCHEMA_LAMBDA_NAME).getValue();

        InvokeResult invokeResult = LambdaUtils.invokeLambdaWithPayload(glueLambdaName,
            constructGluePayload("WrongNamespace", bzDefName, usage, fileType, version));
        verifyErrorInLambdaResponse(invokeResult);

        LogVerification("Invoke Glue lambda with invalid business definition name");
        invokeResult = LambdaUtils.invokeLambdaWithPayload(glueLambdaName,
            constructGluePayload(namespace, "WRONG", usage, fileType, version));
        verifyErrorInLambdaResponse(invokeResult);

        LogVerification("Invoke Glue lambda with invalid usage");
        invokeResult = LambdaUtils.invokeLambdaWithPayload(glueLambdaName,
            constructGluePayload(namespace, bzDefName, "WRONG", fileType, version));
        verifyErrorInLambdaResponse(invokeResult);

        LogVerification("Invoke Glue lambda with invalid fileType");
        invokeResult = LambdaUtils.invokeLambdaWithPayload(glueLambdaName,
            constructGluePayload(namespace, bzDefName, usage, "WRONG", version));
        verifyErrorInLambdaResponse(invokeResult);

        LogVerification("Invoke Glue lambda with invalid schema version");
        invokeResult = LambdaUtils.invokeLambdaWithPayload(glueLambdaName,
            constructGluePayload(namespace, bzDefName, usage, fileType, -1));
        verifyErrorInLambdaResponse(invokeResult);
    }

    private static void verifyErrorInLambdaResponse(InvokeResult result) {
        assertTrue(
            new String(result.getPayload().array()).contains("ERROR. See details in cloudwatch"));
    }

    private void cleanUpBzObject(BusinessObjectData demoBzObject) {
        HerdRestUtil.deleteBusinessObjectData(HERD_ADMIN_USER, demoBzObject);
        HerdRestUtil.deleteBusinessObjectFormat(HERD_ADMIN_USER, demoBzObject);
        HerdRestUtil.deleteBusinessObjectDefinition(HERD_ADMIN_USER, NAMESPACE_MDL,
            demoBzObject.getBusinessObjectDefinitionName());
    }

    private void verifyGluedata(BusinessObjectData demoBzData, String databaseName,
        String tableName) {
        String namespace = demoBzData.getNamespace();
        String bzDefName = demoBzData.getBusinessObjectDefinitionName();

        LogVerification("Verify Herd in Glue");
        waitForGlueDatabase(databaseName, true);

        LogVerification("Verify Herd Column info is correct in Glue");
        waitForGlueTable(databaseName, tableName, true);
        Optional<Table> glueTable = GlueUtils.getGlueTable(databaseName, tableName);
        assertTrue(glueTable.isPresent());
        Schema herdSchema = HerdRestUtil.getBusinessObjectFormatObject(HERD_ADMIN_USER, demoBzData)
            .getSchema();
        verifyColumnInfo(herdSchema, glueTable.get());

        LogVerification("Verify Herd S3 location is correct in Glue");
        verifyS3Location(namespace, bzDefName, glueTable.get());

        LogVerification("Verify Herd Delimeter& Escape Character are correct in Glue");
        Map<String, String> serdeParameters = glueTable.get().getStorageDescriptor().getSerdeInfo()
            .getParameters();
        assertEquals(herdSchema.getEscapeCharacter(), serdeParameters.get("escape.delim"));
        assertEquals(herdSchema.getDelimiter(), serdeParameters.get("field.delim"));
    }

    private void verifyColumnInfo(Schema herdSchema, Table glueTable) {
        LogVerification("Verify Herd column info is correct in Glue");
        List<Column> glueColumns = glueTable.getStorageDescriptor().getColumns();
        List<SchemaColumn> herdColumns = herdSchema.getColumns();
        assertEquals(herdColumns.size(), glueColumns.size());
        glueColumns.forEach(glueCol -> {
            String glueColName = glueCol.getName();
            Optional<SchemaColumn> matchedHerdColumn = (herdColumns.stream()
                .filter(herdCol -> herdCol.getName().equalsIgnoreCase(glueColName)).findFirst());
            assertTrue(matchedHerdColumn.isPresent());
            //column name is case insensitive in glue
            assertTrue(glueCol.getType().equalsIgnoreCase(matchedHerdColumn.get().getType()));
            //null value is n/a in glue
            if (matchedHerdColumn.get().getDescription() == null) {
                assertEquals("n/a", glueCol.getComment());
            } else {
                assertEquals(matchedHerdColumn.get().getDescription(), glueCol.getComment());
            }
        });

        LogVerification("Verify Herd partition is correct in Glue");
        List<Column> gluePartitionKeys = glueTable.getPartitionKeys();
        List<SchemaColumn> herdPartitionKeys = herdSchema.getPartitions();
        assertEquals(herdPartitionKeys.size(), gluePartitionKeys.size());
        gluePartitionKeys.forEach(glueCol -> {
            String glueColName = glueCol.getName();
            Optional<SchemaColumn> matchedHerdColumn = (herdPartitionKeys.stream()
                .filter(herdCol -> herdCol.getName().equalsIgnoreCase(glueColName)).findFirst());
            assertTrue(matchedHerdColumn.isPresent());
        });
    }

    private void verifyS3Location(String namespace, String bzDefName, Table glueTable) {
        Response response = HerdRestUtil.getBusinessObjectData(HERD_ADMIN_USER,
            getDefaultBzOjbData(namespace, bzDefName));
        if (response.statusCode() == HttpStatus.SC_NOT_FOUND) {
            LOGGER.info("data doesn't exist in Herd, not verifying Glue location");
        } else {
            assertEquals(HttpStatus.SC_OK, response.statusCode());
            StorageUnit storageUnit = response.as(BusinessObjectData.class).getStorageUnits()
                .get(0);
            Optional<Attribute> bucketName = storageUnit.getStorage().getAttributes()
                .stream().filter(attribute -> attribute.getName().equals("bucket.name"))
                .findAny();
            assertTrue(bucketName.isPresent());
            String s3Prefix = storageUnit.getStorageDirectory().getDirectoryPath();
            String expectS3Location = "s3://" + bucketName.get().getValue() + "/" + s3Prefix;
            assertTrue(expectS3Location.startsWith(glueTable.getStorageDescriptor().getLocation()));
        }
    }

    private void verifyAthenaData(List<String> colName, List<String> colValue,
        List<Map<String, String>> athenaResult) {
        Optional<Map<String, String>> matchedRow = athenaResult.stream()
            .filter(rowMap -> rowMap.get(colName.get(0)).equals(colValue.get(0))).findFirst();
        assertTrue(matchedRow.isPresent());
        for (int i = 1; i < colName.size(); i++) {
            assertEquals(colValue.get(i), matchedRow.get().get(colName.get(i)));
        }
    }

    private BusinessObjectData getDefaultBzOjbData(String namespace, String bzDefName) {
        BusinessObjectData secBuzObjData = new BusinessObjectData();
        secBuzObjData.setNamespace(namespace);
        secBuzObjData.setBusinessObjectDefinitionName(bzDefName);
        secBuzObjData.setBusinessObjectFormatUsage("MDL");
        secBuzObjData.setBusinessObjectFormatFileType("TXT");
        secBuzObjData.setPartitionKey("TDATE");
        secBuzObjData.setPartitionValue("2017-01-02");
        secBuzObjData.setBusinessObjectFormatVersion(0);
        secBuzObjData.setVersion(0);
        return secBuzObjData;
    }

    private BusinessObjectFormatCreateRequest getFormatCreateRequest(String namespace,
        String bzDefName) {
        BusinessObjectFormatCreateRequest request = new BusinessObjectFormatCreateRequest();
        request.setNamespace(namespace);
        request.setBusinessObjectDefinitionName(bzDefName);
        request.setBusinessObjectFormatUsage("MDL");
        request.setBusinessObjectFormatFileType("TXT");
        request.setPartitionKey("TDATE");
        Schema schema = new Schema();
        SchemaColumn col1 = getColumn("Column1", "INT");
        col1.setRequired(true);
        SchemaColumn col2 = getColumn("Column2", "STRING");
        col2.setRequired(true);
        schema.setColumns(Arrays.asList(col1, col2));

        SchemaColumn partitionCol = getColumn("TDATE", "DATE");
        partitionCol.setRequired(true);
        schema.setPartitions(Collections.singletonList(partitionCol));

        schema.setDelimiter("|");
        schema.setEscapeCharacter("\\");
        schema.setPartitionKeyGroup("TRADE_DT");
        schema.setNullValue("");
        request.setSchema(schema);
        return request;
    }

    private SchemaColumn getColumn(String name, String type) {
        SchemaColumn column = new SchemaColumn();
        column.setName(name);
        column.setType(type);
        return column;
    }

    private String constructGluePayload(String namespace, String businessObjectDefinitionName,
        String usage,
        String fileType, int schemaVersion) {
        return String.format(
            "{\"namespace\": \"%s\",\"businessObjectDefinitionName\": \"%s\",\"businessObjectFormatUsage\": \"%s\","
                + "\"businessObjectFormatFileType\": \"%s\",\"businessObjectFormatVersion\": %d}",
            namespace, businessObjectDefinitionName, usage, fileType, schemaVersion);
    }

    private String constructGlueDbName(String namespace) {
        return "herd__" + namespace.toLowerCase();
    }

    private String constructGlueTableName(String objDefName, String fileType, String usage,
        int version) {
        return String.format("%s_%s_%s_%d", objDefName, usage, fileType, version).toLowerCase();
    }

    private void waitForGlueDatabase(String databaseName, boolean present) {
        given().atMost(5, TimeUnit.MINUTES).pollInterval(10, TimeUnit.SECONDS)
            .until(() -> {
                LOGGER.info("Polling every 10 seconds with timeout: " + 5 + " minutes");
                return GlueUtils.doesDatabaseExist(databaseName) == present;
            });
    }

    private void waitForGlueTable(String databaseName, String tableName, boolean present) {
        given().atMost(5, TimeUnit.MINUTES).pollInterval(10, TimeUnit.SECONDS)
            .until(() -> {
                LOGGER.info("Polling every 10 seconds with timeout: " + 5 + " minutes");
                return GlueUtils.getGlueTable(databaseName, tableName).isPresent() == present;
            });
    }

}
