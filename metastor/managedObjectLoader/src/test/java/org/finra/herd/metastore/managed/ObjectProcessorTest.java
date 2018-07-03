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

import org.finra.herd.metastore.managed.jobProcessor.JobProcessor;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ObjectProcessorTest {

    @Resource
    JdbcTemplate template;

    @Test
    public void testObjProcessorLockRelease()
    {
        ObjectProcessor op = new TestObjectProcessor();
        ClusterManager cm = Mockito.mock(ClusterManager.class);

        when(cm.registerCluster()).thenReturn(true);
        when(cm.getClusterID()).thenReturn("abcd");
        op.clusterManager=cm;
        op.setTemplate(mock(JdbcTemplate.class));

        JobPicker jp = Mockito.mock(JobPicker.class);

        List<JobDefinition> jobList = new ArrayList<>();
        TestJobDefinition j1 = new TestJobDefinition(1L, "ns", "ob", "u", "bz", "2016-09-20", "", "1", null);

        jobList.add(j1);

        TestJobDefinition j2 = new TestJobDefinition(2L, "ns", "ob", "u", "bz", "2016-09-20", "", "1", null);
        jobList.add(j2);
        TestJobDefinition j3 = new TestJobDefinition(3L, "ns", "ob", "u", "bz", "2016-09-20", "", "1", null);
        jobList.add(j3);

        when(jp.findJob(eq("abcd"), anyString())).thenReturn(jobList, new ArrayList<JobDefinition>());
        when(jp.extendLock(eq(j1), eq("abcd"), anyString())).thenReturn(true);
        when(jp.extendLock(eq(j2), eq("abcd"), anyString())).thenReturn(true);
        when(jp.extendLock(eq(j3), eq("abcd"), anyString())).thenReturn(true);

        op.jobPicker=jp;
        op.reEvaluateTimeOutinMsec = 1500;
        op.retryInterval=1000;
        op.maxRetry=3;

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                op.runJobs();
            }
        });

        t.start();
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        t.interrupt();

        assertTrue(j1.isVisited());
        assertTrue(j2.isVisited());
        assertFalse(j3.isVisited());
    }

    class TestJobProcessor extends JobProcessor
    {

        @Override
        protected ProcessBuilder createProcessBuilder(JobDefinition od) {
          return null;
        }

        @Override
        public boolean process(JobDefinition od, String clusterID, String workerID) {
            ((TestJobDefinition)od).setVisited(true);
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException ex){}

            return true;
        }
    }

    class TestJobDefinition extends JobDefinition
    {
        public boolean isVisited() {
            return visited;
        }

        public void setVisited(boolean visited) {
            this.visited = visited;
        }

        boolean visited;


        public TestJobDefinition(long id, String nameSpace, String objectName, String usageCode, String fileType,
                                 String partitionValue, String correlation, String executionID, String partitionKey) {
            super(id, nameSpace, objectName, usageCode, fileType, partitionValue, correlation, executionID, partitionKey);
        }
    }

    class TestObjectProcessor extends ObjectProcessor
    {
        @Override
        protected JobProcessor getJobProcessor(JobDefinition jobDefinition) {
            return new TestJobProcessor();
        }
    }

}


