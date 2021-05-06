package org.finra.herd.metastore.managed.hive;

import com.google.common.collect.Lists;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;

import java.util.List;

@Builder
@Setter
@Getter
@ToString
public class ClusteredDef {
    private String clusterSql;
    private List<ColumnDef> clusteredSortedColDefs;
    private List<String> clusterCols;
    private List<String> sortedCols;
}
