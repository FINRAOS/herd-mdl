package org.finra.herd.metastore.managed.format;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.finra.herd.metastore.managed.JobDefinition;

import java.util.List;

@Builder
@Setter
@Getter
@ToString
public class FormatProcessObject {

    List<String> partitionList;
    JobDefinition jobDefinition;
    Process process;

}
