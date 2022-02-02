package org.finra.herd.metastore.managed.format;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

@Slf4j
public class ProcessAsyncUncaughtExceptionHandler implements AsyncUncaughtExceptionHandler {


    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... objects ) {
        log.error( "Uncaught exception thrown by " + method.getName(), throwable );
        for ( Object param : objects ) {
          log.error("Error --->{}",param);
        }
    }
}
