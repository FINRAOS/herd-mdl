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

import com.google.common.collect.Lists;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Slf4j
@Component
@ToString
@NoArgsConstructor
public class FormatChange {

    List<Pair<ColumnDef, ColumnDef>> nameChanges = Lists.newArrayList();
    List<Pair<ColumnDef, ColumnDef>> typeChanges = Lists.newArrayList();
    List<Pair<ColumnDef,ColumnDef>>  partitionColNameChanges = Lists.newArrayList();
    List<Pair<ColumnDef,ColumnDef>>  partitionColTypeChanges = Lists.newArrayList();
    List<ColumnDef> newColumns = Lists.newArrayList();
    boolean isClusteredSortedChange = false;
    @Autowired
    ClusteredDef clusteredDef;

    boolean escapeStrChanged = false;
    boolean nullStrChanged = false;
    boolean delimChanged = false;
    boolean partitonColumnChanged = false;

    public boolean hasChange()
    {
        return hasColumnChanges()|| hasPartitionColumnChanges() || isClusteredSortedChange;
    }

    public boolean hasColumnChanges()
    {
        return ((nameChanges!=null && !nameChanges.isEmpty()) || (typeChanges!=null && !typeChanges.isEmpty()) || (newColumns!=null && !newColumns.isEmpty()));

    }

    public boolean hasPartitionColumnChanges ()
    {

        partitonColumnChanged = (partitionColTypeChanges!=null && !partitionColTypeChanges.isEmpty());
        return partitonColumnChanged;
    }



}
