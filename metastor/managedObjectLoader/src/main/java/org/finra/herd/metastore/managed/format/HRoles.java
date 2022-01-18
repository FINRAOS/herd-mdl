package org.finra.herd.metastore.managed.format;

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
public abstract class HRoles {


    protected abstract <T> List<T>  getRoles(JobDefinition jobDefinition);


    protected   List<String> grantPrestoRoles(JobDefinition jobDefinition){

        List<String> roles=getRoles(jobDefinition);
        log.info("Roles are ==>{}",roles);

        String dbName=jobDefinition.getObjectDefinition().getDbName();
        String tableName=jobDefinition.getTableName();
        String objName=dbName+"."+tableName;
        if(!roles.isEmpty()){

            return roles.stream().map(role->new String ("GRANT SELECT ON TABLE "+objName+ " TO ROLE "+role+" ;")).collect(Collectors.toList());


        }


        return Collections.emptyList();

    }
}
