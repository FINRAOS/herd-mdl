package org.finra.herd.metastore.managed.conf;

import org.finra.herd.metastore.managed.format.ProcessAsyncUncaughtExceptionHandler;
import org.finra.herd.metastore.managed.util.JobProcessorConstants;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {





    @Override
    @Bean( name = "formatExecutor" )
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(JobProcessorConstants.NO_OF_PARALLEL_EXECUTIONS);
        executor.setMaxPoolSize(JobProcessorConstants.NO_OF_PARALLEL_EXECUTIONS);
        executor.initialize();

        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new ProcessAsyncUncaughtExceptionHandler(  );
    }
}
