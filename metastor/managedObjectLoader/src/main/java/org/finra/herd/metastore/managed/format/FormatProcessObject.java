package org.finra.herd.metastore.managed.format;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.ExecuteResultHandler;
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
    DefaultExecuteResultHandler resultHandler;

}
