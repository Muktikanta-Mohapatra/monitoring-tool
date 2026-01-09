package com.monitoring.logforwarder.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singleton provider for Java 21 virtual thread executor.
 *
 * <p><b>Purpose:</b> Provides a shared virtual thread executor for running blocking
 * I/O operations (database queries, HTTP calls) without blocking platform threads.</p>
 *
 * <p><b>Technical Details:</b></p>
 * <ul>
 *   <li>Uses {@link Executors#newVirtualThreadPerTaskExecutor()}</li>
 *   <li>Automatically creates lightweight virtual threads per task</li>
 *   <li>Registers JVM shutdown hook for graceful termination</li>
 *   <li>Ideal for blocking operations that would starve traditional thread pools</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
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
