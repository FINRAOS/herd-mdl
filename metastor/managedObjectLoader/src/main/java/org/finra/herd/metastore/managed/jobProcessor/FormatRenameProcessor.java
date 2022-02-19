package org.finra.herd.metastore.managed.jobProcessor;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.format.*;
import org.finra.herd.metastore.managed.operations.Grants;
import org.finra.herd.metastore.managed.operations.RenameGrants;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
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
            isComplete = formatUtil.renameExisitingTable(od, clusterID, workerID, getRoles(getRenameObj(od)));

        } catch (Exception ex) {
            logger.severe(ex.getMessage());
            errorBuffer.append(ex.getMessage());
        }

        return isComplete;

    }

    private Optional<List<Grants>> getRenameObj(JobDefinition jobDefinition){

        Gson gson = new Gson();
        renameGrants = gson.fromJson(jobDefinition.getCorrelation(), RenameGrants.class);
        List<Grants> hiveGrants = renameGrants.getHiveGrants();
        log.info("RenameTracker ===>{}", hiveGrants);

        return  Optional.ofNullable(hiveGrants);
    }

    private List<HRoles> getRoles(Optional<List<Grants>> optionalGrantsList) {

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
