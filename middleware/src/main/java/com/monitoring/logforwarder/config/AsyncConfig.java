package com.monitoring.logforwarder.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Value("${app.async-thread-pool-size}")
    private int asyncThreadPoolSize;

    @Value("${app.async-queue-size}")
    private int asyncQueueSize;

    @Bean(name = "eventProcessingExecutor")
    public Executor eventProcessingExecutor() {
        log.info("Configuring eventProcessingExecutor with pool size: {}, queue size: {}", 
            asyncThreadPoolSize, asyncQueueSize);
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        executor.setCorePoolSize(cpuCores * 2);
        executor.setMaxPoolSize(cpuCores * 4);
        executor.setQueueCapacity(asyncQueueSize);
        executor.setThreadNamePrefix("async-event-processor-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        log.info("eventProcessingExecutor configured: core={}, max={}", cpuCores * 2, cpuCores * 4);
        return executor;
    }

    @Bean(name = "kafkaPublishingExecutor")
    public Executor kafkaPublishingExecutor() {
        log.info("Configuring kafkaPublishingExecutor");
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        executor.setCorePoolSize(cpuCores);
        executor.setMaxPoolSize(cpuCores * 2);
        executor.setQueueCapacity(5000);
        executor.setThreadNamePrefix("async-kafka-publisher-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        log.info("kafkaPublishingExecutor configured: core={}, max={}", cpuCores, cpuCores * 2);
        return executor;
    }

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        log.info("Configuring taskScheduler for batch timeout scheduling");
        
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("async-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.initialize();
        
        return scheduler;
    }

    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        log.info("Configuring virtual thread executor for blocking operations");
        return com.monitoring.logforwarder.util.VirtualThreadExecutor.getExecutor();
    }

    @Override
    public Executor getAsyncExecutor() {
        return eventProcessingExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> 
            log.error("Uncaught exception in async method '{}' with parameters {}", 
                method.getName(), params, throwable);
    }
}
