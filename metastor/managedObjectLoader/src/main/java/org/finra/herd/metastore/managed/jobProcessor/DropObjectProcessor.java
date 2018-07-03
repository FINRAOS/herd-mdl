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
import org.finra.herd.metastore.managed.conf.HerdMetastoreConfig;
import org.springframework.stereotype.Component;


@Component
public class DropObjectProcessor extends JobProcessor {
    private static final String SCRIPT_PATH = HerdMetastoreConfig.homeDir+"/metastor/deploy/common/scripts/dropObj/emr_drop_table.sh";

    @Override
    protected ProcessBuilder createProcessBuilder(JobDefinition od) {
        String dbName = od.getObjectDefinition().getDbName();
        String tblName=od.getActualObjectName()+"_"+od.getObjectDefinition().getUsageCode()+"_"+od.getObjectDefinition().getFileType();
        tblName = tblName.replaceAll("\\.","_").replaceAll(" ", "_").replaceAll("-","_");

        ProcessBuilder pb = new ProcessBuilder("sh", SCRIPT_PATH, dbName, tblName);
        return pb;
    }
}
