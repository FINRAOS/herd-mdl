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
package org.finra.herd.metastore.managed;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Date;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = HerdMetastoreTest.class)
public class JobPickerTest {

    @Resource
    JdbcTemplate template;

    JobPicker jp = new JobPicker();

    @Before
    public void setup()
    {
        jp.template = template;
    }

    @Test
    @Transactional
    public void testLockObjNewLock()
    {
        String workerID = String.valueOf(Thread.currentThread().getId());
        assertTrue(jp.lockObj(new JobDefinition(1L,"A","B","C","D", "","","",""), "C111", workerID));
        assertTrue(jp.lockObj(new JobDefinition(2L,"A","B","C","D", "","","",""), "C111", workerID));
        assertFalse(jp.lockObj(new JobDefinition(3L,"A","B","C","D", "","","",""), "C222", workerID));

        assertTrue(jp.lockObj(new JobDefinition(4L,"A2","B2","C2","D2", "","","",""), "C111", workerID));
        assertFalse(jp.lockObj(new JobDefinition(5L,"A2","B2","C2","D2", "","","",""), "C111","2"));
    }

    @Test
    @Transactional
    public void testLockSawCompactionObj()
    {
        String correlation = "{\n" +
                "  \"businessObject\": {\n" +
                "    \"late_reporting_for\":\"ABC\"\n" +
                "  }\n" +
                "}";
        assertTrue("First Lock", jp.lockObj(new JobDefinition(1L, "SAW", "ABC", "PRC","BZ", "","","",""),"C111", "1"));
        assertFalse(jp.lockObj(new JobDefinition(2L, "SAW", "ABC_COMPACTION", "PRC","BZ", "",correlation,"",""),"C222", "1"));

        assertTrue("First Lock", jp.lockObj(new JobDefinition(3L, "SAW", "ABC_COMPACTION", "PRC-2","BZ","",correlation,"",""),"C111", "1"));
        assertFalse("First Lock", jp.lockObj(new JobDefinition(4L,"SAW", "ABC_COMPACTION", "PRC-2","BZ", "",correlation,"",""),"C222", "1"));
        assertFalse(jp.lockObj(new JobDefinition(5L, "SAW", "ABC", "PRC-2","BZ", "",correlation,"",""),"C222", "1"));
    }

    @Test
    @Transactional
    public void testDeleteExpiredLock() throws IOException
    {
        String sql = new String(Files.readAllBytes(Paths.get("src/test/resources/dbunit/object_lock_dataset.sql")));

        template.update(sql);

        assertEquals(3, template.queryForList("select * from METASTOR_OBJECT_LOCKS").size());
        jp.deleteExpiredLocks();
        assertEquals(2, template.queryForList("select * from METASTOR_OBJECT_LOCKS").size());
    }

    @Test
    @Transactional
    public void testExtendLock() throws IOException
    {
        String sql = new String(Files.readAllBytes(Paths.get("src/test/resources/dbunit/object_lock_dataset.sql")));

        template.update(sql);

        Date originalExpiration = template.queryForObject("select EXPIRATION_DT FROM METASTOR_OBJECT_LOCKS where CLUSTER_ID='C01'", Date.class);
        assertTrue(originalExpiration.before(Calendar.getInstance().getTime()));
        jp.lockObj(new JobDefinition(10L, "A1","B1","C","D", "","","",""), "C01", "1");

        Date newExpiration = template.queryForObject("select EXPIRATION_DT FROM METASTOR_OBJECT_LOCKS where CLUSTER_ID='C01'", Date.class);

        Date expectedDt = template.queryForObject("select TIMESTAMPADD(MINUTE, 5, now());", Date.class);

        assertTrue(Math.abs(expectedDt.getTime() - newExpiration.getTime()) < 5000 );
    }

    @Test
    @Transactional
    public void testFindJob() throws IOException
    {
        String sql = new String(Files.readAllBytes(Paths.get("src/test/resources/dbunit/job_picker_dataset.sql")));

        template.update(sql);

        String sql1 = new String(Files.readAllBytes(Paths.get("src/test/resources/dbunit/object_lock_dataset.sql")));

        template.update(sql1);
        assertEquals(2, jp.findJob("C03", "1").size());

        assertEquals(1, template.queryForList("select * from METASTOR_OBJECT_LOCKS where CLUSTER_ID='C03'").size());

    }

    @Test
    @Transactional
    public void testFindJobWithRetry() throws IOException
    {
        String sql = new String(Files.readAllBytes(Paths.get("src/test/resources/dbunit/DM_NOTIFICATION_DATASET.sql")));
        String[] sqlStatements = sql.split(";");
        template.batchUpdate(sqlStatements);
        jp.maxRetry=3;

        jp.jobRetryIntervalInSecs=30;
        assertEquals(1, jp.findJob("C03", "1").size());

        jp.jobRetryIntervalInSecs=80;
        assertEquals(0, jp.findJob("C03", "1").size());
    }

}
