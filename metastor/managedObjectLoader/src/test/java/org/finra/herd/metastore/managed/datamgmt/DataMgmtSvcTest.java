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
package org.finra.herd.metastore.managed.datamgmt;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.sdk.invoker.ApiClient;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectDataDdl;
import org.finra.herd.metastore.managed.JobDefinition;
import org.springframework.beans.factory.annotation.Autowired;

//@RunWith(SpringJUnit4ClassRunner.class)
//@SpringBootTest(classes = HerdMetastoreTest.class)
//@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
public class DataMgmtSvcTest {

    @Autowired
    ApiClient apiClient;


  //  @Test
    public void testGetDataDDLSingletonNone() throws ApiException
    {
        DataMgmtSvc dataMgmtSvc = new DataMgmtSvc();
        dataMgmtSvc.dmApiClient = apiClient;
        JobDefinition jd = new JobDefinition(0L, "HUB", "TRADE_CLNDR", "PRC", "bz",
                "2015-08-28__2015-08-27", null, null, "PARTITION");
        jd.setWfType(1);
        BusinessObjectDataDdl ddl =dataMgmtSvc.getBusinessObjectDataDdl(jd, Lists.newArrayList("2015-08-28"));
        log.info(ddl.getDdl());
    }
}
