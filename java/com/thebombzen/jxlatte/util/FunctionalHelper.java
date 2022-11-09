package com.thebombzen.jxlatte.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class FunctionalHelper {
    private FunctionalHelper() {

    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow0(Throwable ex) throws E {
        throw (E) ex;
    }

    public static <E> E sneakyThrow(Throwable e) {
        Throwable ex = e;
        while (ex instanceof CompletionException) {
            Throwable cause = ex.getCause();
            if (cause != null)
                ex = cause;
            else
                break;
        }
        FunctionalHelper.<RuntimeException>sneakyThrow0(ex);
        return null;
    }

    public static <U> Supplier<U> uncheck(ExceptionalSupplier<U> supplier) {
        return supplier.uncheck();
    }

    public static <T, U> Supplier<U> constant(Function<T, U> func, T input) {
        return () -> func.apply(input);
    }

    public static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            return sneakyThrow(ex);
        }
    }

    /**
     * Joins all of the futures and preserves their order
     */
    public static <T> T[] join(IntFunction<T[]> generator, Iterable<? extends CompletableFuture<?>> futures) {
        List<Object> results = new ArrayList<>();
        for (CompletableFuture<?> future : futures) {
            results.add(join(future));
        }
        if (generator != null)
            return results.stream().toArray(generator);
        
        return null;
    }

    public static void join(Iterable<? extends CompletableFuture<?>> tasks) {
        join(null, tasks);
    }

    /**
     * Joins all of the futures and preserves their order
     */
    public static <T> T[] join(IntFunction<T[]> generator, CompletableFuture<?>... futures) {
        return join(generator, Arrays.asList(futures));
    }
}
