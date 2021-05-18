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
package org.finra.herd.metastore.managed.jobProcessor;

import com.google.common.collect.Lists;
import junit.framework.TestCase;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.HerdMetastoreTest;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.NotificationSender;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.finra.herd.metastore.managed.hive.*;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = HerdMetastoreTest.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
public class HiveHqlGeneratorTest {


    public static final String DM_DDL = "CREATE EXTERNAL TABLE IF NOT EXISTS `string` (\n" +
            "        `REC_UNIQUE_ID` BIGINT,\n" +
            "        `ORGNL_TRADE_DT` DATE,\n" +
            "        `MKT_CNTR_ID` VARCHAR(1),\n" +
            "        `MBR_ID` VARCHAR(4),\n" +
            "        `FIRM_MP_ID` VARCHAR(4),\n" +
            "        `FIRM_CRD_NB` INT,\n" +
            "        `MBR_CLRG_NB` VARCHAR(4),\n" +
            "        `REC_LOAD_DT` DATE,\n" +
            "        `NEW_COLUMN` VARCHAR(6))\n" +
            "    PARTITIONED BY (`TRADE_DT` DATE, `TEST` DECIMAL(18,8))\n" +
            "    ROW FORMAT DELIMITED FIELDS TERMINATED BY '\001' ESCAPED BY '\\\\' NULL DEFINED AS ''\n" +
            "    STORED AS TEXTFILE;";

    @Test public void testSchemaSqlNoChange() throws SQLException, ApiException, IOException {
        HiveClient hiveClient = Mockito.mock(HiveClient.class);
        List columnList= Lists.newArrayList(new ColumnDef("REC_UNIQUE_ID", "BIGINT",0),
                new ColumnDef("ORGNL_TRADE_DT", "DATE",1),
                new ColumnDef("MKT_CNTR_ID", "VARCHAR(1)",2),
                new ColumnDef("MBR_ID", "VARCHAR(4)",3),
                new ColumnDef("FIRM_MP_ID", "VARCHAR(4)",4),
                new ColumnDef("FIRM_CRD_NB", "INT",5),
                new ColumnDef("MBR_CLRG_NB", "VARCHAR(4)",6),
                new ColumnDef("REC_LOAD_DT", "DATE",7),
                new ColumnDef("NEW_COLUMN", "VARCHAR(6)",8));
        
        when(hiveClient.getExistingDDL(eq("metastor"), eq("test_managed_obj_test_orc")))
                .thenReturn(new HiveTableSchema().toBuilder().columns(columnList).build());
        when(hiveClient.tableExist(eq("metastor"), eq("test_managed_obj_test_orc"))).thenReturn(true);

        HiveHqlGenerator hiveHqlGenerator = new HiveHqlGenerator();

        DataMgmtSvc dataMgmtSvc = Mockito.mock(DataMgmtSvc.class);

        hiveHqlGenerator.dataMgmtSvc = dataMgmtSvc;

        hiveHqlGenerator.hiveClient=hiveClient;
        hiveHqlGenerator.notificationSender=Mockito.mock(NotificationSender.class);

        JobDefinition jd = new JobDefinition(0L, "metastor", "test_managed_obj", "test", "orc",
                "2015-08-28", null, "aa", "trade_dt");

        BusinessObjectFormat format = new BusinessObjectFormat();
        format.setBusinessObjectFormatVersion(1);
        when(dataMgmtSvc.getDMFormat(eq(jd))).thenReturn(format);
        when(dataMgmtSvc.getTableSchema(eq(jd), eq(false))).thenReturn(DM_DDL);

        List<String> result = hiveHqlGenerator.schemaSql(true, jd);

        assertTrue(result.size() == 0);

    }

    @Test public void testSchemaSqlChange() throws SQLException, ApiException, IOException {
        HiveClient hiveClient = Mockito.mock(HiveClient.class);

        List columnList= Lists.newArrayList(new ColumnDef("REC_UNIQUE_ID", "BIGINT",0),
                new ColumnDef("ORGNL_TRADE_DT", "DATE",1),
                new ColumnDef("MKT_CNTR_ID", "VARCHAR(1)",2),
                new ColumnDef("MBR_ID", "VARCHAR(4)",3),
                new ColumnDef("FIRM_MP_ID", "VARCHAR(4)",4),
                new ColumnDef("FIRM_CRD_NB", "INT",5),
                new ColumnDef("MBR_CLRG_NB", "VARCHAR(2)",6));

        when(hiveClient.getExistingDDL(eq("metastor"), eq("test_managed_obj_test_orc")))
                .thenReturn(new HiveTableSchema().toBuilder().columns(columnList).build());
        when(hiveClient.tableExist(eq("metastor"), eq("test_managed_obj_test_orc"))).thenReturn(true);

        HiveHqlGenerator hiveHqlGenerator = new HiveHqlGenerator();
        DataMgmtSvc dataMgmtSvc = Mockito.mock(DataMgmtSvc.class);

        hiveHqlGenerator.dataMgmtSvc = dataMgmtSvc;

        hiveHqlGenerator.hiveClient=hiveClient;
        hiveHqlGenerator.notificationSender=Mockito.mock(NotificationSender.class);

        JobDefinition jd = new JobDefinition(0L, "metastor", "test_managed_obj", "test", "orc",
                "2015-08-28", null, "aa", "trade_dt");
        BusinessObjectFormat format = new BusinessObjectFormat();
        format.setBusinessObjectFormatVersion(1);
        when(dataMgmtSvc.getDMFormat(eq(jd))).thenReturn(format);
        when(dataMgmtSvc.getTableSchema(eq(jd), eq(false))).thenReturn(DM_DDL);
        List<String> result = hiveHqlGenerator.schemaSql(true, jd);

        assertEquals("format cahnge", 2, result.size());

        for(String s:result)
        {
            assertTrue("Contains Cascade", s.contains("CASCADE"));
        }

    }

    @Test
    public void testDetectDiff() throws Exception
    {
        HiveHqlGenerator hiveHqlGenerator = new HiveHqlGenerator();
        HiveTableSchema hiveTableSchema = HiveClientTest.getTestHiveTableSchema();
        HiveClient hiveClient = Mockito.mock(HiveClient.class);
        hiveHqlGenerator.hiveClient=hiveClient;
        hiveHqlGenerator.notificationSender=Mockito.mock(NotificationSender.class);



        String ddl = "CREATE EXTERNAL TABLE IF NOT EXISTS `string` (\n" +
                "    `DATE` DATE,\n" +
                "    `ACTIVITY_NAME` VARCHAR(35),\n" +
                "    `TRANSACTN_DT_KEY` DECIMAL(10),\n" +
                "    `OPT_PROD_KEY` DECIMAL(21),\n" +
                "    `OPT_SYMBOL` VARCHAR(10),\n" +
                "    `OPT_EXP_YR_MTH` DECIMAL(20),\n" +
                "    `STRK_PRC` DECIMAL(20,8),\n" +
                "    `PUT_CALL_CLASS` CHAR(1),\n" +
                "    `CM` VARCHAR(5),\n" +
                "    `FIRM` VARCHAR(100),\n" +
                "    `CRD` VARCHAR(8),\n" +
                "    `EXCEPTION_CAT` VARCHAR(15),\n" +
                "    `OCC_POS_TRNSFR_ADJ_ID` VARCHAR(10),\n" +
                "    `REC_ID` DECIMAL(10),\n" +
                "    `CM_OPEN_POS` DECIMAL(20),\n" +
                "    `OPTION_OPEN_POS` DECIMAL(20),\n" +
                "    `XCPTN_ID` BIGINT)\n" +
                "PARTITIONED BY (`begindate` DATE)\n" +
                "ROW FORMAT DELIMITED FIELDS TERMINATED BY '\001' ESCAPED BY '\\' NULL DEFINED AS '\\N'\n" +
                "STORED AS TEXTFILE;";

        BusinessObjectFormat format = new BusinessObjectFormat();
        format.setBusinessObjectFormatVersion(0);

        FormatChange change = hiveHqlGenerator.detectSchemaChange(new JobDefinition(1,"MRP","OPT_OCCADJ_OPENINT_DETAIL",
                "PRC","BZ", "","","",""), hiveTableSchema, format, ddl);

        TestCase.assertFalse("There is no change", change.hasChange());
    }

    @Test
    public void testPartitionColChange() throws Exception
    {
        HiveHqlGenerator hiveHqlGenerator = new HiveHqlGenerator();

        HiveTableSchema hiveTableSchema = HiveClientTest.getTestHiveTableSchema();

        String ddl = "CREATE EXTERNAL TABLE IF NOT EXISTS `string` (\n" +
                "    `TDATE` DATE,\n" +
                "    `ACTIVITY_NAME` VARCHAR(35),\n" +
                "    `TRANSACTN_DT_KEY` DECIMAL(10),\n" +
                "    `OPT_PROD_KEY` DECIMAL(21),\n" +
                "    `OPT_SYMBOL` VARCHAR(10),\n" +
                "    `OPT_EXP_YR_MTH` DECIMAL(20),\n" +
                "    `STRK_PRC` DECIMAL(20,8),\n" +
                "    `PUT_CALL_CLASS` CHAR(1),\n" +
                "    `CM` VARCHAR(5),\n" +
                "    `FIRM` VARCHAR(100),\n" +
                "    `CRD` VARCHAR(8),\n" +
                "    `EXCEPTION_CAT` VARCHAR(15),\n" +
                "    `OCC_POS_TRNSFR_ADJ_ID` VARCHAR(10),\n" +
                "    `REC_ID` DECIMAL(10),\n" +
                "    `CM_OPEN_POS` DECIMAL(20),\n" +
                "    `OPTION_OPEN_POS` DECIMAL(20),\n" +
                "    `XCPTN_ID` BIGINT)\n" +
                "PARTITIONED BY (`begindate` DATE, `test` DECIMAL(20,8))\n" +
                "ROW FORMAT DELIMITED FIELDS TERMINATED BY '\001' ESCAPED BY '\\' NULL DEFINED AS '\\N'\n" +
                "STORED AS TEXTFILE;";

        BusinessObjectFormat format = new BusinessObjectFormat();
        format.setBusinessObjectFormatVersion(0);
        hiveHqlGenerator.notificationSender=Mockito.mock(NotificationSender.class);
        FormatChange change = hiveHqlGenerator.detectSchemaChange(new JobDefinition(1,"MRP","OPT_OCCADJ_OPENINT_DETAIL",
                "PRC","BZ", "","","",""), hiveTableSchema, format, ddl);

        TestCase.assertTrue("There is change", change.hasChange());
        TestCase.assertTrue("There is change", change.hasPartitionColumnChanges());
    }


    @Test
    public void testClusterByChange() throws Exception
    {
        HiveHqlGenerator hiveHqlGenerator = new HiveHqlGenerator();

        HiveTableSchema hiveTableSchema = HiveClientTest.getClusterByTestHiveTableSchema();

        String ddl = "CREATE EXTERNAL TABLE `tst_all_chng_cat_ola_events_prc_orc`(\n" +
                "`event_type_cd` varchar(10),\n" +
                "`cat_lifecycle_id` bigint,\n" +
                "`cat_venue_odr_id` varchar(100),\n" +
                "`top_ind` varchar(1),\n" +
                "`cat_frm_dsgnt_id` varchar(40),\n" +
                "`odr_id` varchar(64),\n" +
                "`rtd_odr_id_1` varchar(64),\n" +
                "`quote_id` varchar(64),\n" +
                "`trd_id` varchar(64),\n" +
                "`side_cd` varchar(20),\n" +
                "`eqty_sym_id` varchar(22),\n" +
                "`osi_sym_id` varchar(22),\n" +
                "`cat_rptr_crdid` varchar(20),\n" +
                "`cat_rptr_id` varchar(10),\n" +
                "`sndr_id` varchar(20),\n" +
                "`sndr_crd_id` bigint,\n" +
                "`dstnt_id` varchar(20),\n" +
                "`dstnt_crd_id` bigint,\n" +
                "`event_dt` date,\n" +
                "`event_tm` decimal(15,9),\n" +
                "`event_qty` decimal(18,6),\n" +
                "`mkt_cntr_id` varchar(7),\n" +
                "`rec_unq_id` varchar(120),\n" +
                "`file_ts` decimal(23,9),\n" +
                "`raw_record` string)\n" +
                "PARTITIONED BY (\n" +
                "`trade_dt` date,\n" +
                "`trans_type_cd` varchar(1))\n" +
                "CLUSTERED BY (\n" +
                "cat_lifecycle_id,\n" +
                "cat_rptr_id_1 \n" +
                ")\n" +
                "SORTED BY (\n" +
                "cat_lifecycle_id ASC,\n" +
                "event_tm ASC)\n" +
                "INTO 5000 BUCKETS\n" +
                "ROW FORMAT SERDE\n" +
                "'org.apache.hadoop.hive.ql.io.orc.OrcSerde'\n" +
                "WITH SERDEPROPERTIES (\n" +
                "'escape.delim'='',\n" +
                "'field.delim'='',\n" +
                "'serialization.format'='',\n" +
                "'serialization.null.format'='')\n" +
                "STORED AS INPUTFORMAT\n" +
                "'org.apache.hadoop.hive.ql.io.orc.OrcInputFormat'\n" +
                "OUTPUTFORMAT\n" +
                "'org.apache.hadoop.hive.ql.io.orc.OrcOutputFormat'\n" +
                "LOCATION\n" +
                "'s3://3019-1075-9931-appdata-us-east-1/CATMETA/cat_diver.db/cat_ola_events_prc_orc'\n" +
                "TBLPROPERTIES (\n" +
                "'transient_lastDdlTime'='1604439427');\n";

        BusinessObjectFormat format = new BusinessObjectFormat();
        format.setBusinessObjectFormatVersion(0);
        hiveHqlGenerator.notificationSender=Mockito.mock(NotificationSender.class);
        FormatChange change = hiveHqlGenerator.detectSchemaChange(new JobDefinition(1,"MRP","tst_all_chng_cat_ola_events",
                "PRC","BZ", "","","",""), hiveTableSchema, format, ddl);


        System.out.println("change is"+change.getClusteredDef().getClusterSql());
        System.out.println("change has"+change.hasChange());

        TestCase.assertTrue("There is change", change.hasChange());
    }


    @Test
    public void testAllFormatByChange() throws Exception
    {
        HiveHqlGenerator hiveHqlGenerator = new HiveHqlGenerator();

        HiveTableSchema hiveTableSchema = HiveClientTest.getAllFormatTestHiveTableSchema();

        String ddl = "CREATE EXTERNAL TABLE `tst_all_chng_cat_ola_events_prc_orc`(\n" +
                "`event_type_cd` varchar(10),\n" +
                "`cat_lifecycle_id` bigint,\n" +
                "`cat_venue_odr_id` varchar(100),\n" +
                "`top_ind` varchar(1),\n" +
                "`cat_frm_dsgnt_id` varchar(40),\n" +
                "`odr_id` varchar(64),\n" +
                "`rtd_odr_id_1` varchar(64),\n" +
                "`quote_id` varchar(64),\n" +
                "`trd_id` varchar(64),\n" +
                "`side_cd` varchar(20),\n" +
                "`eqty_sym_id` varchar(22),\n" +
                "`osi_sym_id` varchar(22),\n" +
                "`cat_rptr_crdid` varchar(20),\n" +
                "`cat_rptr_id` varchar(10),\n" +
                "`sndr_id` varchar(20),\n" +
                "`sndr_crd_id` bigint,\n" +
                "`dstnt_id` varchar(20),\n" +
                "`dstnt_crd_id` bigint,\n" +
                "`event_dt` date,\n" +
                "`event_tm` decimal(15,9),\n" +
                "`event_qty` decimal(18,6),\n" +
                "`mkt_cntr_id` varchar(8),\n" +
                "`rec_unq_id` varchar(120),\n" +
                "`file_ts` decimal(23,9),\n" +
                "`raw_record` string)\n" +
                "PARTITIONED BY (\n" +
                "`trade_dt` date,\n" +
                "`trans_type_cd` varchar(3))\n" +
                "CLUSTERED BY (\n" +
                "cat_lifecycle_id,\n" +
                "cat_rptr_id_1 \n" +
                ")\n" +
                "SORTED BY (\n" +
                "cat_lifecycle_id ASC,\n" +
                "event_tm ASC)\n" +
                "INTO 5000 BUCKETS\n" +
                "ROW FORMAT SERDE\n" +
                "'org.apache.hadoop.hive.ql.io.orc.OrcSerde'\n" +
                "WITH SERDEPROPERTIES (\n" +
                "'escape.delim'='',\n" +
                "'field.delim'='',\n" +
                "'serialization.format'='',\n" +
                "'serialization.null.format'='')\n" +
                "STORED AS INPUTFORMAT\n" +
                "'org.apache.hadoop.hive.ql.io.orc.OrcInputFormat'\n" +
                "OUTPUTFORMAT\n" +
                "'org.apache.hadoop.hive.ql.io.orc.OrcOutputFormat'\n" +
                "LOCATION\n" +
                "'s3://3019-1075-9931-appdata-us-east-1/CATMETA/cat_diver.db/cat_ola_events_prc_orc'\n" +
                "TBLPROPERTIES (\n" +
                "'transient_lastDdlTime'='1604439427');\n";

        BusinessObjectFormat format = new BusinessObjectFormat();
        format.setBusinessObjectFormatVersion(0);
        hiveHqlGenerator.notificationSender=Mockito.mock(NotificationSender.class);
        FormatChange change = hiveHqlGenerator.detectSchemaChange(new JobDefinition(1,"MRP","tst_all_chng_cat_ola_events",
                "PRC","BZ", "","","",""), hiveTableSchema, format, ddl);



        TestCase.assertTrue(change.hasPartitionColumnChanges());
        TestCase.assertTrue(change.hasColumnChanges());

        TestCase.assertTrue("There is change", change.hasChange());
    }


}
