package org.finra.herd.metastore.managed.jobProcessor;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.format.*;
import org.finra.herd.metastore.managed.operations.Grants;
import org.finra.herd.metastore.managed.operations.RenameGrants;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class FormatRenameProcessor extends JobProcessor {


    @Autowired
    JobProcessorConstants jobProcessorConstants;

    @Autowired
    FormatUtil formatUtil;


    @Autowired
    RenameGrants renameGrants;



    @Override
    public boolean process(JobDefinition od, String clusterID, String workerID) {

        boolean isComplete=false;
        try {
            jobPicker.extendLock(od, clusterID, workerID);
            setPartitionKeyIfNotPresent( od );
            isComplete = formatUtil.renameExisitingTable(od, clusterID, workerID, getUserSuppliedRoles(getUserSuppliedGrants(od)));

        } catch (Exception ex) {
            logger.severe(ex.getMessage());
            errorBuffer.append(ex.getMessage());
        }

        return isComplete;

    }

    private Optional<List<Grants>> getUserSuppliedGrants(JobDefinition jobDefinition){

        Gson gson = new Gson();
        String correlationData=jobDefinition.getCorrelation();
        if(correlationData!=null) {
            renameGrants = gson.fromJson(correlationData, RenameGrants.class);
            List<Grants> hiveGrants = renameGrants.getHiveGrants();
            log.info("hiveGrants supplied by user ===>{}", hiveGrants);
            return  Optional.ofNullable(hiveGrants);

        }else {

            return Optional.ofNullable(null);
        }

    }

    private List<HRoles> getUserSuppliedRoles(Optional<List<Grants>> optionalGrantsList) {

        List<HRoles> hRoles = new ArrayList<>();


        if (optionalGrantsList.isPresent() ) {

            List<Grants> grantsList= optionalGrantsList.get();

            if( grantsList!=null && grantsList.size() > 0) {
                for (Grants grants : grantsList) {
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
