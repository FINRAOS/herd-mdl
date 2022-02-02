package org.finra.herd.metastore.managed.format;

import com.google.common.collect.Lists;
import lombok.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Component
public class ClusteredDef {
    private String clusterSql;
    private List<ColumnDef> clusteredSortedColDefs = Lists.newArrayList();
    private List<String> clusterCols = Lists.newArrayList();
    private List<String> sortedCols = Lists.newArrayList();
}
