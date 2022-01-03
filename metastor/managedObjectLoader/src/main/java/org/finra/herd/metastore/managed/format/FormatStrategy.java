package org.finra.herd.metastore.managed.format;

import org.finra.herd.metastore.managed.JobDefinition;

import java.util.List;

public interface FormatStrategy {


    public void executeFormatChange(FormatChange formatChange, JobDefinition jd, List<String> list, String tableName,boolean isCascade);
}
