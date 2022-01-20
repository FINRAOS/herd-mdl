package org.finra.herd.metastore.managed.format;

import lombok.ToString;
import org.finra.herd.metastore.managed.JobDefinition;

import java.text.Format;

public interface FormatStrategy {

    public boolean hasFormatCompleted();
    public void executeFormatChange(JobDefinition jd, FormatChange formatChange,boolean cascade,String clusterID, String workerID);
    public String getErr();
}
