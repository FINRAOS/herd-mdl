package org.finra.herd.metastore.managed.format;

import org.finra.herd.metastore.managed.JobDefinition;

import java.text.Format;

public interface FormatStrategy {

    public boolean hasFormatCompleted();
    public void executeFormatChange(JobDefinition jd, FormatChange formatChange,boolean cascade);
}
