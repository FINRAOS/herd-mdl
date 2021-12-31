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

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;
import org.finra.herd.metastore.managed.hive.ColumnDef;
import org.finra.herd.metastore.managed.format.FormatChange;
import org.finra.herd.metastore.managed.hive.HiveTableSchema;
import org.junit.Test;

import java.util.List;


@Slf4j
public class NotificationSenderTest {


    @Test
    public void testEmailFormat() throws Exception
    {
        NotificationSender sender = new NotificationSender();

        ColumnDef old = ColumnDef.builder().index(1)
                .name("old").type("varchar").build();

        ColumnDef newC = ColumnDef.builder().index(1)
            .name("newName").type("int").build();

        Pair<ColumnDef, ColumnDef> p = new Pair<ColumnDef, ColumnDef>(old, newC);

//        formatChange.setNameChanges(Lists.newArrayList(p));
//        formatChange.setTypeChanges(Lists.newArrayList(p));
//        formatChange.setNewColumns(Lists.newArrayList(newC));
//        formatChange.setDelimChanged(true);
//        formatChange.setEscapeStrChanged(true);
//        formatChange.setNullStrChanged(true);
//
        FormatChange formatChange = FormatChange.builder()
				.nameChanges(Lists.newArrayList(p))
				.typeChanges(Lists.newArrayList(p))
                .newColumns(Lists.newArrayList(newC))
				.delimChanged( true )
				.escapeStrChanged( true )
				.nullStrChanged( true )
				.build();



        List columnList1 = Lists.newArrayList(new ColumnDef("REC_UNIQUE_ID", "BIGINT",0),
                new ColumnDef("ORGNL_TRADE_DT", "DATE",1),
                new ColumnDef("MKT_CNTR_ID", "VARCHAR(1)",2),
                new ColumnDef("MBR_ID", "VARCHAR(4)",3),
                new ColumnDef("FIRM_MP_ID", "VARCHAR(4)",4),
                new ColumnDef("FIRM_CRD_NB", "INT",5),
                new ColumnDef("MBR_CLRG_NB", "VARCHAR(4)",6),
                new ColumnDef("REC_LOAD_DT", "DATE",7));

        List columnList2 = Lists.newArrayList(new ColumnDef("REC_UNIQUE_ID", "BIGINT",0),
                new ColumnDef("ORGNL_TRADE_DT", "DATE",1),
                new ColumnDef("MKT_CNTR_ID", "VARCHAR(1)",2),
                new ColumnDef("MBR_ID", "VARCHAR(4)",3),
                new ColumnDef("FIRM_MP_ID", "VARCHAR(4)",4),
                new ColumnDef("FIRM_CRD_NB", "INT",5),
                new ColumnDef("MBR_CLRG_NB", "VARCHAR(4)",6),
                new ColumnDef("REC_LOAD_DT", "DATE",7),
                new ColumnDef("NEW_COLUMN", "VARCHAR(6)",8));

		log.info("Email Format (All Changes included):\n{}",
				sender.getFormatChangeMsg(formatChange,
											2,
											new JobDefinition(1, "ns", "obj", "prc", "txt", "", null, "", ""),
											HiveTableSchema.builder().columns(columnList1).partitionColumns( columnList1 ).build(),
											HiveTableSchema.builder().columns(columnList2).partitionColumns( columnList2 ).build())
				);
    }
}
