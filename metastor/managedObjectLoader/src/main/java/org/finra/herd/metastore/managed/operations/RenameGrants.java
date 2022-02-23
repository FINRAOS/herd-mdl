package org.finra.herd.metastore.managed.operations;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ToString
@Getter
public class RenameGrants {

    @SerializedName("grants")
    List<Grants> hiveGrants;
}
