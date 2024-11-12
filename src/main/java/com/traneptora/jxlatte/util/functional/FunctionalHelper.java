package com.traneptora.jxlatte.util.functional;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class FunctionalHelper {
    private FunctionalHelper() {

    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow0(Throwable ex) throws E {
        throw (E) ex;
    }

    public static <E> E sneakyThrow(Throwable e) {
        Throwable ex = e;
        while (ex instanceof CompletionException || ex instanceof InvocationTargetException) {
            Throwable cause = ex.getCause();
            if (cause != null)
                ex = cause;
            else
                break;
        }
        FunctionalHelper.<RuntimeException>sneakyThrow0(ex);
        return null;
    }

    public static <T, U> Supplier<U> constant(Function<T, U> func, T input) {
        final U result = func.apply(input);
        return () -> result;
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
    public static <T> T[] join(IntFunction<T[]> generator, Iterator<? extends CompletableFuture<?>> futures) {
        List<Object> results = new ArrayList<>();
        while (futures.hasNext()) {
            CompletableFuture<?> future = futures.next();
            results.add(join(future));
        }
        if (generator != null)
            return results.stream().toArray(generator);

        return null;
    }

    /**
     * Joins all of the futures and preserves their order
     */
    public static <T> T[] join(IntFunction<T[]> generator, CompletableFuture<?>... futures) {
        return join(generator, Stream.of(futures).iterator());
    }
}
