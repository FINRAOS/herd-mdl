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

import org.finra.herd.metastore.managed.JobDefinition;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class ManagedJobProcessorTest {

    @Test
    public void testProcessJob()
    {
        ManagedJobProcessor jobProcessor = new ManagedJobProcessor();
        JobDefinition jobDefinition = new JobDefinition(1L, "test", "obj", "usage",
                "ft", "2016-08-12", "null", "111", "TRADE_DT");

        jobProcessor.process(jobDefinition, "","");
    }

    @Test
    public void testGetProcessBuilder()
    {
        ManagedJobProcessor jobProcessor = new ManagedJobProcessor();
        jobProcessor.hiveHqlGenerator = Mockito.mock(HiveHqlGenerator.class);
        ProcessBuilder pb = jobProcessor.createProcessBuilder(new JobDefinition(1L, "test", "obj", "usage",
                "ft", "2016-08-12", "null", "111", "TRADE_DT"));
        assertTrue(pb.command().get(0).startsWith("hive"));

    }

    @Test(expected = NullPointerException.class)
    public void testNullPartitionKey()
    {
        ManagedJobProcessor jobProcessor = new ManagedJobProcessor();
        jobProcessor.hiveHqlGenerator = Mockito.mock(HiveHqlGenerator.class);
        ProcessBuilder pb = jobProcessor.createProcessBuilder(new JobDefinition(1L, "test", "obj", "usage",
                "ft", "2016-08-12", "null", "111", null));

    }

    @Test
    public void testNullPartitionKeyFailure()
    {
        ManagedJobProcessor jobProcessor = new ManagedJobProcessor();
        assertFalse(jobProcessor.process(new JobDefinition(1L, "test", "obj", "usage",
                "ft", "2016-08-12", "null", "111", "test"), "",""));

    }


}
