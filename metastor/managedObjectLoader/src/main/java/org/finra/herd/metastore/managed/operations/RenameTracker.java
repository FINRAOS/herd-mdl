package org.finra.herd.metastore.managed.operations;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.format.HRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Builder
@Getter
@ToString
public class RenameTracker {

    String existingTableName;
    String desiredTableName;
    @SerializedName("grants")
    List<Grants> hiveGrants;

}
