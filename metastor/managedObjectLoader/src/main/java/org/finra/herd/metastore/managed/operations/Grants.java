package org.finra.herd.metastore.managed.operations;

import lombok.Getter;

@Getter
public class Grants {

    String princicpalName;
    String principalType;
    String privilege;
    boolean grantOptions;
}
