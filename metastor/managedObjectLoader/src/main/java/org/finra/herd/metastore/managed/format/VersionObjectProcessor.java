package org.finra.herd.metastore.managed.format;

import lombok.extern.slf4j.Slf4j;
import org.finra.herd.metastore.managed.JobDefinition;
import org.finra.herd.metastore.managed.jobProcessor.BackLoadObjectProcessor;
import org.finra.herd.metastore.managed.jobProcessor.HiveHqlGenerator;
import org.finra.herd.metastore.managed.jobProcessor.JobProcessor;
import org.finra.herd.metastore.managed.util.MetastoreUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class VersionObjectProcessor  {


    @Autowired
    BackLoadObjectProcessor backLoadObjectProcessor;



}
