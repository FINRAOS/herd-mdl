package org.finra.herd.metastore.managed.operations;

import lombok.Getter;
import lombok.ToString;
import org.springframework.stereotype.Component;

@Getter
@Component
@ToString
public class Grants {

    String princicpalName;
    String principalType;
    String privilege;
    boolean grantOptions;
}
