package com.monitoring.logforwarder.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class AsyncExecutors {

    @Autowired
    private ApplicationContext applicationContext;

    public static final String EVENT_PROCESSING_EXECUTOR = "eventProcessingExecutor";
    public static final String KAFKA_PUBLISHING_EXECUTOR = "kafkaPublishingExecutor";

    public Executor getEventProcessingExecutor() {
        return applicationContext.getBean(EVENT_PROCESSING_EXECUTOR, Executor.class);
    }

    public Executor getKafkaPublishingExecutor() {
        return applicationContext.getBean(KAFKA_PUBLISHING_EXECUTOR, Executor.class);
    }

    public CompletableFuture<Void> executeAsync(Runnable task) {
        return executeAsyncWithExecutor(task, EVENT_PROCESSING_EXECUTOR);
    }

    public CompletableFuture<Void> executeAsyncWithExecutor(Runnable task, String executorName) {
        try {
            Executor executor = applicationContext.getBean(executorName, Executor.class);
            return CompletableFuture.runAsync(task, executor)
                .exceptionally(ex -> {
                    log.error("Error executing async task with executor: {}", executorName, ex);
                    return null;
                });
        } catch (Exception ex) {
            log.error("Failed to get executor: {}", executorName, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    public <T> CompletableFuture<T> executeAsyncWithResult(Callable<T> task) {
        return executeAsyncWithResultAndExecutor(task, EVENT_PROCESSING_EXECUTOR);
    }

    public <T> CompletableFuture<T> executeAsyncWithResultAndExecutor(Callable<T> task, String executorName) {
        try {
            Executor executor = applicationContext.getBean(executorName, Executor.class);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return task.call();
                } catch (Exception ex) {
                    log.error("Error executing async task with result using executor: {}", executorName, ex);
                    throw new RuntimeException(ex);
                }
            }, executor)
            .exceptionally(ex -> {
                log.error("Async task failed with executor: {}", executorName, ex);
                throw new RuntimeException(ex);
            });
        } catch (Exception ex) {
            log.error("Failed to get executor: {}", executorName, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }
}
