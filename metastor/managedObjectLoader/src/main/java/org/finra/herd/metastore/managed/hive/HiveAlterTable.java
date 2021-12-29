package org.finra.herd.metastore.managed.hive;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.datamgmt.DataMgmtSvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class HiveAlterTable {

    @Autowired
    protected DataMgmtSvc dataMgmtSvc;


    public void formatRegularColumn(FormatChange change, JobDefinition jd, List<String> list, String tableName) throws org.finra.herd.sdk.invoker.ApiException
    {
        if (change.hasColumnChanges()) {
            boolean cascade = cascade(jd);
            String cascadeStr = "";
            if (cascade) {
                cascadeStr = "CASCADE";
            }
            if (!jd.getObjectDefinition().getFileType().equalsIgnoreCase("ORC")) {
                String sql = dataMgmtSvc.getTableSchema(jd, true);


                if (cascade) {
                    sql = sql.substring(0, sql.lastIndexOf(";")) + " CASCADE;";
                }
                list.add(sql);
            } else {
                for (Pair<ColumnDef, ColumnDef> pair : change.getNameChanges()) {
                    ColumnDef existing = pair.getFirst();
                    ColumnDef newColum = pair.getSecond();

                    list.add(String.format("Alter table %s change %s %s %s %s;", tableName,
                        existing.getName(), newColum.getName(), newColum.getType(), cascadeStr));



                }

                for (Pair<ColumnDef, ColumnDef> pair : change.getTypeChanges()) {
                    ColumnDef existing = pair.getFirst();
                    ColumnDef newColum = pair.getSecond();
                    list.add(String.format("Alter table %s change %s %s %s %s;", tableName,
                        existing.getName(), newColum.getName(), newColum.getType(), cascadeStr));
                }

                if (!change.getNewColumns().isEmpty()) {
                    StringBuffer sb = new StringBuffer();
                    for (ColumnDef c : change.getNewColumns()) {
                        sb.append(String.format("%s %s,", c.getName(), c.getType()));
                    }
                    sb.deleteCharAt(sb.length() - 1);
                    list.add(String.format("Alter table %s add columns (%s) %s;", tableName, sb.toString(),
                        cascadeStr));
                }


                log.info("the formatRegularColumn list is :{}",list);
            }
        }


    }

    public void formatClusterColumn(FormatChange change,JobDefinition jd,List<String> list,String tableName)  {

        if(change.isClusteredSortedChange())
        {

            log.info("Clustered Change :Alter table {}  {}", tableName, change.getClusteredDef().getClusterSql());
            list.add(String.format("Alter table %s  %s ;", tableName,
                change.getClusteredDef().getClusterSql()));


        }

    }

    public void formatPartitionColumn(FormatChange change, JobDefinition jd, List<String> list, String tableName) {

        if(change.hasPartitionColumnChanges())
        {


            for (Pair<ColumnDef, ColumnDef> pair : change.getPartitionColTypeChanges()) {
                ColumnDef existing = pair.getFirst();
                ColumnDef newColum = pair.getSecond();
                list.add(String.format("Alter table %s partition column ( %s %s);", tableName,
                    newColum.getName(), newColum.getType()));
            }


            log.info("the formatPartitionColumn list is :{}",list);

        }


    }

    protected boolean cascade(JobDefinition jd) {
        return true;
    }



}
