package com.monitoring.logforwarder.util;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Utility class for async operation helpers and reactive conversions.
 *
 * <p><b>Purpose:</b> Provides utility methods for executing tasks asynchronously
 * using virtual threads, and converting between Reactor Mono/Flux and CompletableFuture.</p>
 *
 * <p><b>Key Methods:</b></p>
 * <ul>
 *   <li>{@link #executeAsyncFuture} - Execute blocking code on virtual threads</li>
 *   <li>{@link #toCompletableFuture} - Convert Mono/Flux to CompletableFuture</li>
 *   <li>{@link #toMono} - Convert CompletableFuture to Mono</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
public class AsyncHelper {

    private static final ExecutorService virtualThreadExecutor = VirtualThreadExecutor.getExecutor();

    public AsyncHelper() {
    }

    public static <T> CompletableFuture<T> toCompletableFuture(Mono<T> mono) {
        return mono.toFuture();
    }

    public static <T> CompletableFuture<java.util.List<T>> toCompletableFuture(Flux<T> flux) {
        return flux.collectList().toFuture();
    }

    public static <T> Mono<T> toMono(CompletableFuture<T> future) {
        return Mono.fromFuture(future);
    }

    public static <T> Mono<T> executeAsync(Supplier<T> supplier) {
        return Mono.fromCallable(supplier::get)
            .subscribeOn(Schedulers.fromExecutorService(virtualThreadExecutor));
    }

    public static <T> CompletableFuture<T> executeAsyncFuture(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, virtualThreadExecutor);
    }

    public static <T> Mono<Void> executeAsyncVoid(Runnable runnable) {
        return Mono.fromRunnable(runnable)
            .subscribeOn(Schedulers.fromExecutorService(virtualThreadExecutor))
            .then();
    }

    public static <T> CompletableFuture<Void> executeAsyncVoidFuture(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, virtualThreadExecutor);
    }

    public static <T> CompletableFuture<T> handleAsyncException(
        CompletableFuture<T> future, 
        String operationName) {
        return future.exceptionally(ex -> {
            log.error("Async operation '{}' failed with error: {}", operationName, ex.getMessage(), ex);
            throw new RuntimeException("Async operation failed: " + operationName, ex);
        });
    }

    public static <T> Mono<T> handleAsyncException(
        Mono<T> mono, 
        String operationName) {
        return mono.onErrorMap(ex -> {
            log.error("Async operation '{}' failed with error: {}", operationName, ex.getMessage(), ex);
            return new RuntimeException("Async operation failed: " + operationName, ex);
        });
    }

    public static <T> Mono<T> withTimeout(Mono<T> mono, java.time.Duration timeout) {
        return mono.timeout(timeout, 
            Mono.error(new java.util.concurrent.TimeoutException("Operation timed out after " + timeout)));
    }

    public static <T> Flux<T> withTimeout(Flux<T> flux, java.time.Duration timeout) {
        return flux.timeout(timeout, 
            Flux.error(new java.util.concurrent.TimeoutException("Operation timed out after " + timeout)));
    }
}
