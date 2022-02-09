package org.finra.herd.metastore.managed.format;

import com.google.common.collect.Lists;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Component
@Builder
@Getter
public  class HRoles {

    String dbName;
    String tableName;
    String principalName;
    String principalType;
    String privilege;
    boolean grantOption;




   }
