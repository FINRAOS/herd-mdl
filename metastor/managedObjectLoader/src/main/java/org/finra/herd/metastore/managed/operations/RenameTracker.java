package org.finra.herd.metastore.managed.operations;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.format.HRoles;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Builder
@Getter
public class RenameTracker {

    String existingTableName;
    String desiredTableName;
    List<Grants> hiveGrants;

}
