package org.finra.herd.metastore.managed.jobProcessor;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.format.*;
import org.finra.herd.metastore.managed.jobProcessor.dao.PartitionsDAO;
import org.finra.herd.metastore.managed.operations.Grants;
import org.finra.herd.metastore.managed.operations.RenameTracker;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.finra.herd.sdk.invoker.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class RenameObjectProcessor extends JobProcessor {


    @Autowired
    JobProcessorConstants jobProcessorConstants;

    @Autowired
    FormatUtil formatUtil;


    @Override
    public boolean process(JobDefinition od, String clusterID, String workerID) {

        boolean isComplete=false;
        try {
            jobPicker.extendLock(od, clusterID, workerID);
            Optional<RenameTracker> optionalRenameTracker=getRenameObj(od);

            if(optionalRenameTracker.isPresent()){
                RenameTracker renameTracker= optionalRenameTracker.get();


                Optional<String> newTableName = Optional.ofNullable(renameTracker.getDesiredTableName());
                Optional<String>  currentTableName = Optional.ofNullable(renameTracker.getExistingTableName());
                isComplete = formatUtil.renameExisitingTable(od, clusterID, workerID,currentTableName, newTableName, getRoles(optionalRenameTracker));

            }else{
                isComplete = formatUtil.renameExisitingTable(od, clusterID, workerID,Optional.empty(), Optional.empty(), getRoles(optionalRenameTracker));

            }



        } catch (Exception ex) {
            logger.severe(ex.getMessage());
            errorBuffer.append(ex.getMessage());
        }

        return isComplete;

    }

    private Optional<RenameTracker> getRenameObj(JobDefinition jobDefinition){
        Gson gson = new Gson();
        RenameTracker renameTracker = gson.fromJson(jobDefinition.getCorrelation(), RenameTracker.class);
        log.info("RenameTracker ===>{}", renameTracker);

        return  Optional.ofNullable(renameTracker);
    }

    private List<HRoles> getRoles(Optional<RenameTracker> optionalRenameTracker) {

        List<HRoles> hRoles = new ArrayList<>();


        if (optionalRenameTracker.isPresent() ) {

            RenameTracker renameTracker = optionalRenameTracker.get();
            List<Grants> grantsList= renameTracker.getHiveGrants();

            if( grantsList!=null && renameTracker.getHiveGrants().size() > 0) {
                for (Grants grants : renameTracker.getHiveGrants()) {
                    hRoles.add(HRoles.builder()
                            .principalName(grants.getPrincicpalName())
                            .principalType(grants.getPrincipalType())
                            .privilege(grants.getPrivilege())
                            .grantOption(grants.isGrantOptions())
                            .build()
                    );
                }
            }
        }


        return hRoles;
    }


    @Override
    protected ProcessBuilder createProcessBuilder(JobDefinition od) {

        //Will never be called.
        return null;
    }


}
