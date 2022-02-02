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
package org.finra.herd.metastore.managed.hive;

import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.HerdMetastoreTest;
import org.finra.herd.metastore.managed.format.ColumnDef;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.TestCase.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = HerdMetastoreTest.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
public class HiveClientTest {

    @Test
    public void testGetDMColumns()
    {
        String ddl = "CREATE EXTERNAL TABLE IF NOT EXISTS `string` (\n" +
                "        `REC_UNIQUE_ID` BIGINT,\n" +
                "        `ORGNL_TRADE_DT` DATE,\n" +
                "        `MKT_CNTR_ID` VARCHAR(1),\n" +
                "        `MBR_ID` VARCHAR(4),\n" +
                "        `FIRM_MP_ID` VARCHAR(4),\n" +
                "        `FIRM_CRD_NB` INT,\n" +
                "        `MBR_CLRG_NB` VARCHAR(4),\n" +
                "        `REC_LOAD_DT` DECIMAL(18, 8),\n" +
                "        `NEW_COLUMN` VARCHAR(6))\n" +
                "    PARTITIONED BY (`TRADE_DT` DATE, `TEST` DECIMAL(18,8))\n" +
                "    ROW FORMAT DELIMITED FIELDS TERMINATED BY '\001' ESCAPED BY '\\\\' NULL DEFINED AS ''\n" +
                "    STORED AS TEXTFILE;";

        HiveTableSchema schema = HiveClientImpl.getHiveTableSchema(ddl);
        List<ColumnDef> result = schema.getColumns();

        assertTrue("Column size", result.size()==9);

        assertEquals("NEW_COLUMN", result.get(8).getName());
        assertEquals("VARCHAR(6)", result.get(8).getType());
        assertEquals("REC_UNIQUE_ID", result.get(0).getName());
        assertEquals("DECIMAL(18,8)", result.get(7).getType());

        assertEquals("Delimiter", "\001", schema.getDelim());
        assertEquals("Partition Column size", 2, schema.getPartitionColumns().size());
        assertEquals("Partition Column Name", "TRADE_DT", schema.getPartitionColumns().get(0).getName());
        assertEquals("Partition Column type", "DATE", schema.getPartitionColumns().get(0).getType());
        assertEquals("Escape", "\\\\", schema.getEscape());
        assertEquals("null char", "", schema.getNullChar());

    }

    @Test
    public void testGetSchemaFromHive()
    {
        HiveTableSchema schema = getTestHiveTableSchema();
        List<ColumnDef> result = schema.getColumns();

        assertTrue("Column size", result.size()==17);

        assertEquals("Delimiter", "\u0001", schema.getDelim());
        assertEquals("Escape", "\\", schema.getEscape());
        assertEquals("null char", "\\N", schema.getNullChar());
        assertEquals("Partition Column size", 1, schema.getPartitionColumns().size());
        assertEquals("Partition Column Name", "begindate", schema.getPartitionColumns().get(0).getName());
        assertEquals("Partition Column type", "date", schema.getPartitionColumns().get(0).getType());


    }

    public static HiveTableSchema getClusterByTestHiveTableSchema() {
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
                "cat_rptr_id\n" +
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

        return HiveClientImpl.getHiveTableSchema(ddl);
    }


    public static HiveTableSchema getAllFormatTestHiveTableSchema() {
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
                "cat_rptr_id\n" +
                ")\n" +
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

        return HiveClientImpl.getHiveTableSchema(ddl);
    }

    public static HiveTableSchema getTestHiveTableSchema() {
        String ddl = "CREATE EXTERNAL TABLE `OPT_OCCADJ_OPENINT_DETAIL_PRC_BZ`(\n" +
                "  `date` date,\n" +
                "  `activity_name` varchar(35),\n" +
                "  `transactn_dt_key` decimal(10,0),\n" +
                "  `opt_prod_key` decimal(21, 0),\n" +
                "  `opt_symbol` varchar(10),\n" +
                "  `opt_exp_yr_mth` decimal(20,0),\n" +
                "  `strk_prc` decimal(20,8),\n" +
                "  `put_call_class` char(1),\n" +
                "  `cm` varchar(5),\n" +
                "  `firm` varchar(100),\n" +
                "  `crd` varchar(8),\n" +
                "  `exception_cat` varchar(15),\n" +
                "  `occ_pos_trnsfr_adj_id` varchar(10),\n" +
                "  `rec_id` decimal(10,0),\n" +
                "  `cm_open_pos` decimal(20,0),\n" +
                "  `option_open_pos` decimal(20,0),\n" +
                "  `xcptn_id` bigint)\n" +
                "PARTITIONED BY (\n" +
                "  `begindate` date)\n" +
                "ROW FORMAT DELIMITED\n" +
                "  FIELDS TERMINATED BY '\u0001'\n" +
                "  NULL DEFINED AS '\\N'\n" +
                "WITH SERDEPROPERTIES (\n" +
                "  'escape.delim'='\\')\n" +
                "STORED AS INPUTFORMAT\n" +
                "  'org.apache.hadoop.mapred.TextInputFormat'\n" +
                "OUTPUTFORMAT\n" +
                "  'org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat'\n" +
                "LOCATION\n" +
                "  's3://s3-bucket/METASTOR/mrp.db/opt_occadj_openint_detail_prc_bz'\n" +
                "TBLPROPERTIES (\n" +
                "  'last_modified_by'='hadoop', \n" +
                "  'last_modified_time'='1487966199', \n" +
                "  'transient_lastDdlTime'='1487966199')";

        return HiveClientImpl.getHiveTableSchema(ddl);
    }

    @Test
    public void testSameChar()
    {
        assertTrue(HiveTableSchema.isSameChar("\u0001","\001"));
        assertTrue(HiveTableSchema.isSameChar("\u0002","\002"));
        assertTrue(HiveTableSchema.isSameChar("\\u0002","\\002"));
        assertFalse(HiveTableSchema.isSameChar("\\", "\\001"));
        assertFalse(HiveTableSchema.isSameChar("\\", "|"));
        assertTrue(HiveTableSchema.isSameChar("\\", "\\"));
    }

    @Test
    public void testGetHive2Schema()
    {
        String ddl = "CREATE EXTERNAL TABLE `C2_RST_OPT_XCPTN_PRC_BZ`(\n" +
                "`xcptn_type_nm` varchar(20),\n" +
                "`mkt_cntr_id` varchar(2),\n" +
                "`crd_id` varchar(10),\n" +
                "`product_id` bigint,\n" +
                "`resp_type_cd` varchar(1),\n" +
                "`resp_ent_id` bigint,\n" +
                "`trade_date` date,\n" +
                "`class_sym` varchar(10),\n" +
                "`put_call_code` char(1),\n" +
                "`expr_date` date,\n" +
                "`exer_price` decimal(20,8),\n" +
                "`under_sec_sym` varchar(10),\n" +
                "`open_int_qty` bigint,\n" +
                "`prev_open_int_qty` bigint,\n" +
                "`sod_long_qty` bigint,\n" +
                "`sod_short_qty` bigint,\n" +
                "`eod_long_qty` bigint,\n" +
                "`eod_short_qty` bigint,\n" +
                "`acct_orig_code` char(1),\n" +
                "`sub_acct_code` varchar(3),\n" +
                "`xcptn_id` bigint,\n" +
                "`user_acr` varchar(20),\n" +
                "`rst_xcptn_id` bigint)\n" +
                "PARTITIONED BY (\n" +
                "`begindate` date)\n" +
                "ROW FORMAT SERDE\n" +
                "'org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe'\n" +
                "WITH SERDEPROPERTIES (\n" +
                "'escape.delim'='\\\\',\n" +
                "'field.delim'='\\u0001',\n" +
                "'serialization.format'='\\u0001',\n" +
                "'serialization.null.format'='\\\\N')\n" +
                "STORED AS INPUTFORMAT\n" +
                "'org.apache.hadoop.mapred.TextInputFormat'\n" +
                "OUTPUTFORMAT\n" +
                "'org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat'\n" +
                "LOCATION\n" +
                "'s3://s3-bucket/METASTOR/mrp.db/c2_rst_opt_xcptn_prc_bz'\n" +
                "TBLPROPERTIES (\n" +
                "'last_modified_by'='hadoop',\n" +
                "'last_modified_time'='1486059836',\n" +
                "'transient_lastDdlTime'='1486059836')";

        HiveTableSchema schema = HiveClientImpl.getHiveTableSchema(ddl);
        assertEquals("Delimiter", "\\u0001", schema.getDelim());
        assertEquals("Null Char", "\\\\N", schema.getNullChar());
        assertEquals("escape", "\\\\", schema.getEscape());
    }
}
