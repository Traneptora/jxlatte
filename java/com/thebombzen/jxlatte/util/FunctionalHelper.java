package com.thebombzen.jxlatte.util;

import java.util.concurrent.CompletionException;
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
}
