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
import org.finra.herd.metastore.managed.JobPicker;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertFalse;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JobProcessorTest {

	public static final String PARTITION_KEY = "test";

	@Test
    public void testStats()
    {
        JobProcessor jp = new JobProcessor() {
            @Override
            protected ProcessBuilder createProcessBuilder(JobDefinition od) {
                return null;
            }
        };

        String correlation = "{\"processing\": { \"collectStatistics\" : \"NONE\"}}";

        JobDefinition od = new JobDefinition(1L, "","","","","",correlation, "1", PARTITION_KEY);
        assertFalse(jp.collectStats(od));

        correlation = "{\"processing\": { \"collectStatistics\" : \"N\"}}";
        od = new JobDefinition(1L, "","","","","",correlation, "1", PARTITION_KEY);
        assertTrue(jp.collectStats(od));

    }

    @Test
    public void testAutoLockExtention()
    {
        JobProcessor jp = new JobProcessor() {
            @Override
            protected ProcessBuilder createProcessBuilder(JobDefinition od) {
                ProcessBuilder pb = new ProcessBuilder("sh", "target/test-classes/longsleep.sh", "1");
                return pb;
            }
        };

        JobPicker jobPicker = Mockito.mock(JobPicker.class);
        jp.setJobPicker(jobPicker);

        when(jobPicker.extendLock(any(JobDefinition.class), anyString(), anyString())).thenReturn(true);
        assertEquals(false, jp.process(new JobDefinition(1L, "NS", "ON", "U", "FT", "2016-09-09", null, null, PARTITION_KEY), "CC", "1"));

        verify(jobPicker).extendLock(eq(new JobDefinition(1L, "NS", "ON", "U", "FT", "2016-09-09", null, null, PARTITION_KEY ))
                , eq("CC"), eq("1"));
    }
}
