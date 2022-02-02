package org.finra.herd.metastore.managed.format;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.ExecuteResultHandler;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.JobPicker;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@Slf4j
@Scope("prototype")
public class FormatUtil {


    @Autowired
    public FormatUtil() {}


    public String getAlterTableStatemts(Optional<String> ddl) {

        String[] ddlArr = null;
        if (ddl.isPresent()) {
            ddlArr = ddl.get().split(";");
            return ddlArr[1] + ";";
        } else {
            log.info("DDL is empty ==> No cookie for you!");
        }

        return null;
    }


}
