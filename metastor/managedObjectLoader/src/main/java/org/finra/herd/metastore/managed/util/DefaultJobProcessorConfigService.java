package org.finra.herd.metastore.managed.util;

import org.springframework.stereotype.Component;

@Component
public class DefaultJobProcessorConfigService implements  JobProcessorConstants{

    @Override
    public int getAlterTableAddMaxPartitions() {
        return 35000;
    }

    @Override
    public int getMaxPartitionFormatLimit() {
        return 50000;
    }
}
