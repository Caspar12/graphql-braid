package com.atlassian.braid.java.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.function.Function.identity;

/**
 * A set of functions to help working with futures
 */
public final class BraidFutures {

    private BraidFutures() {
    }

    @SafeVarargs
    public static <T, R> CompletableFuture<R> all(Collector<T, ?, R> collector, CompletableFuture<T>... futures) {
        return all(identity(), collector, futures);
    }

    @SafeVarargs
    public static <T, A, R> CompletableFuture<R> all(Function<T, A> transform, Collector<A, ?, R> collector, CompletableFuture<T>... futures) {
        return CompletableFuture.allOf(futures).thenApply(__ -> mapAndCollect(transform, collector, futures));
    }

    @SafeVarargs
    private static <T, A, R> R mapAndCollect(Function<T, A> transform, Collector<A, ?, R> collector, CompletableFuture<T>... futures) {
        return Stream.of(futures).map(BraidFutures::safeGet).map(transform).collect(collector);
    }

    private static <T> T safeGet(CompletableFuture<T> completableFuture) {
        try {
            return (T) completableFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException();
        }
    }
}