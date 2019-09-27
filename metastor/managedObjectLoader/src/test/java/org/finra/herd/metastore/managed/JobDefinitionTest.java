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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JobDefinitionTest {

    @Test
    public void testGetActualName()
    {
        String correlation = "{\n" +
                "  \"businessObject\": {\n" +
                "    \"late_reporting_for\":\"ABC\"\n" +
                "  }\n" +
                "}";

        JobDefinition jd = new JobDefinition(3L, "SAW", "ABC_COMPACTION", "PRC-2","BZ","",correlation,"","");

        assertEquals("ABC", jd.getActualObjectName());
    }

	@Test
	public void testIdentifyObjectName()
	{
		String correlation = "{\n" +
				"  \"businessObject\": {\n" +
				"    \"original_object_name\":\"ABC\"\n" +
				"  }\n" +
				"}";

		JobDefinition jd = new JobDefinition(3L, "METASTORE", "ABC_LATE", "PRC-2","BZ","",correlation,"","");

		assertEquals("ABC_PRC_2_BZ", jd.getTableName());
	}

	@Test
	public void testIdentifyObjectNameWhenCorrelationIsNull()
	{

		JobDefinition jd = new JobDefinition(3L, "SAW", "ABC_COMPACTION", "PRC-2","BZ","",null,"","");

		assertEquals("ABC_COMPACTION_PRC_2_BZ", jd.getTableName());
	}

    @Test
    public void testGetActualNameIllegalJson()
    {
        String correlation = "<!CDATA[{\n" +
                "  \"businessObject\": {\n" +
                "    \"late_reporting_for\":\"ABC\"\n" +
                "  }\n" +
                "}]]>";

        JobDefinition jd = new JobDefinition(3L, "SAW", "ABC_COMPACTION", "PRC-2","BZ","",correlation,"","");

        assertEquals("ABC_COMPACTION", jd.getActualObjectName());
    }

    @Test
    public void testSpecialCharacter()
    {
        String[][] testObjs = {
                {"meta-test","table-test","prc-test-1","txt"},
                {"meta test","table test","prc test 1","txt"},
                {"meta.test","table.test","prc.test.1","txt"}
        } ;

        for(String[] s:testObjs)
        {
            JobDefinition jd = new JobDefinition(100L, s[0], s[1], s[2], s[3],"","","","");

            assertEquals("Name space special character replacement", "meta_test", jd.getObjectDefinition().getDbName());
            assertEquals("table name special character replacement", "table_test_prc_test_1_txt", jd.getTableName());
        }
    }
}
