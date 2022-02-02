package org.finra.herd.metastore.managed.format;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.exec.DefaultExecutor;
import org.finra.herd.metastore.managed.JobDefinition;
import org.springframework.context.annotation.Scope;

import java.util.List;

@Builder
@Setter
@Getter
@ToString
@Scope("prototype")
public class FormatProcessObject {

    List<String> partitionList;
    JobDefinition jobDefinition;
    DefaultExecutor defaultExecutor;
    int exitValue;

}
