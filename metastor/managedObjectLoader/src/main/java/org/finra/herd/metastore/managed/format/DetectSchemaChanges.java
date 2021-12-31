package org.finra.herd.metastore.managed.format;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.NotificationSender;
import org.finra.herd.metastore.managed.hive.ClusteredDef;
import org.finra.herd.metastore.managed.hive.ColumnDef;
import org.finra.herd.metastore.managed.hive.HiveClientImpl;
import org.finra.herd.metastore.managed.hive.HiveTableSchema;
import org.finra.herd.sdk.invoker.ApiException;
import org.finra.herd.sdk.model.BusinessObjectFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class DetectSchemaChanges {


    @Autowired
    protected NotificationSender notificationSender;

    public FormatChange detectSchemaChange(JobDefinition jd,
                                    HiveTableSchema existingHiveTableSchema,
                                    BusinessObjectFormat format, String ddl) throws ApiException {


        HiveTableSchema newSchema = HiveClientImpl.getHiveTableSchema(ddl);
        List<ColumnDef> existingColumns = existingHiveTableSchema.getColumns();
        List<ColumnDef> existingPartitionColumns = existingHiveTableSchema.getPartitionColumns();
        List<ColumnDef> newColumns = newSchema.getColumns();
        List<ColumnDef> newPartitionColumns = newSchema.getPartitionColumns();
        ClusteredDef existingClusteredDef = existingHiveTableSchema.getClusteredDef();
        ClusteredDef newClusterDef = newSchema.getClusteredDef();


        log.info("Existing Partition columns = " + existingPartitionColumns.size() + ", ddl from Herd  Partitioncolumns = " + newPartitionColumns.size());

        log.info("Existing columns = " + existingColumns.size() + ", ddl from Herd has columns = " + newColumns.size());


        FormatChange formatChange = FormatChange.builder().build();

        detectandSetRegularColumnChanges(existingColumns,newColumns,formatChange);
        detectandSetPartitionColumnChanges(existingPartitionColumns,newPartitionColumns,formatChange);

        if(detectClusterSortedColChanges(existingClusteredDef,newClusterDef))
        {
            formatChange.setClusteredSortedChange(true);
            formatChange.setClusteredDef(newClusterDef);
        }


        if (formatChange.hasChange() ) {
            notificationSender.sendFormatChangeEmail(formatChange, format.getBusinessObjectFormatVersion(), jd,
                existingHiveTableSchema, newSchema);
        }

        return formatChange;
    }

    boolean detectClusterSortedColChanges(ClusteredDef existing,ClusteredDef recent){

        boolean isClusterSortedChg = false;
        List<ColumnDef> existingColumns = existing.getClusteredSortedColDefs();
        List<ColumnDef> newColumns = recent.getClusteredSortedColDefs();
        log.info("existing Cluster by Sorted by Columns:{}",existingColumns);
        log.info("recent Cluster by Sorted by Columns:{} ",newColumns);

        if ((existingColumns !=null && !existingColumns.isEmpty()) && (newColumns !=null && !newColumns.isEmpty()) )  {
            int minColumns = Math.min(existingColumns.size(),newColumns.size());

            for (int i = 0; i < minColumns; i++) {
                ColumnDef old = existingColumns.get(i);
                ColumnDef newColum = newColumns.get(i);

                if (!newColum.getName().equalsIgnoreCase(old.getName()) )
                {
                    isClusterSortedChg = true;
                }
            }

        }else if ((existingColumns == null || existingColumns.isEmpty()) && (newColumns !=null && !newColumns.isEmpty())  )
        {
            isClusterSortedChg = true;

        }

        log.info("is there a Cluster or Sorted Column Change?:{}",isClusterSortedChg);
        return isClusterSortedChg;
    }

    @VisibleForTesting
    void detectandSetRegularColumnChanges(List<ColumnDef> existingColumns, List<ColumnDef> newColumns,FormatChange formatChange)
    {
        List<Pair<ColumnDef, ColumnDef>> nameChanges = Lists.newArrayList();
        List<Pair<ColumnDef, ColumnDef>> typeChanges = Lists.newArrayList();
        List<ColumnDef> addedColumns = Lists.newArrayList();



        int minColumns = Math.min(existingColumns.size(), newColumns.size());

        //@TODO - Refactor and Move all of these logic out of HqlGenerator when we implement Format Change api using sns
            /*
              Regular Column
             */
        for (int i = 0; i < minColumns; i++) {
            ColumnDef existing = existingColumns.get(i);
            ColumnDef newColum = newColumns.get(i);

            if (!newColum.getName().equalsIgnoreCase(existing.getName())) {
                nameChanges.add(new Pair<>(existing, newColum));
            } else if (!newColum.isSameType(existing)) {
                typeChanges.add(new Pair<>(existing, newColum));
            }

        }

        if (newColumns.size() > existingColumns.size()) {
            for (int i = existingColumns.size(); i < newColumns.size(); i++) {
                ColumnDef newColum = newColumns.get(i);
                addedColumns.add(newColum);
            }
        }

        log.info("Regular Column Changes nameChanges :{}, typeChanges:{},addedColumns :{}",nameChanges,typeChanges,addedColumns);

        formatChange.setNameChanges(nameChanges);
        formatChange.setTypeChanges(typeChanges);
        formatChange.setNewColumns(addedColumns);


    }

    @VisibleForTesting
    void detectandSetPartitionColumnChanges(List<ColumnDef> existingPartitionColumns, List<ColumnDef> newPartitionColumns,FormatChange formatChange)
    {
        List<Pair<ColumnDef, ColumnDef>> partitionColTypeChanges = Lists.newArrayList();
        List<Pair<ColumnDef, ColumnDef>> partitionColNameChanges = Lists.newArrayList();


        int minColumns = Math.min(existingPartitionColumns.size(), newPartitionColumns.size());


        for (int i = 0; i < minColumns; i++) {
            ColumnDef existing = existingPartitionColumns.get(i);
            ColumnDef newColum = newPartitionColumns.get(i);

            if (!newColum.getName().equalsIgnoreCase(existing.getName())) {
                partitionColNameChanges.add(new Pair<>(existing, newColum));
                log.error("Hive does not support partition Column Name changes:{}",partitionColNameChanges);

            } else if (!newColum.isSameType(existing)) {
                partitionColTypeChanges.add(new Pair<>(existing, newColum));
            }

        }

        formatChange.setPartitionColNameChanges(partitionColNameChanges);
        formatChange.setPartitionColTypeChanges(partitionColTypeChanges);


    }


}
