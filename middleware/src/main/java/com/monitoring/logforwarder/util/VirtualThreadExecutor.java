package com.monitoring.logforwarder.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class VirtualThreadExecutor {

    private static final ExecutorService virtualThreadExecutor = 
        Executors.newVirtualThreadPerTaskExecutor();

    static {
        log.info("Initializing virtual thread executor for async blocking operations");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down virtual thread executor");
            virtualThreadExecutor.shutdown();
            try {
                if (!virtualThreadExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.warn("Virtual thread executor did not terminate in time");
                    virtualThreadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for virtual thread executor shutdown", e);
                virtualThreadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    private VirtualThreadExecutor() {
    }

    public static ExecutorService getExecutor() {
        return virtualThreadExecutor;
    }

    public static boolean isShutdown() {
        return virtualThreadExecutor.isShutdown();
    }

    public static boolean isTerminated() {
        return virtualThreadExecutor.isTerminated();
    }
}
