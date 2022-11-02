package com.thebombzen.jxlatte;

import java.util.function.Supplier;

import com.thebombzen.jxlatte.io.IOHelper;

@FunctionalInterface
public interface ExceptionalSupplier<U> {
    
    public U supply() throws Throwable;

    public default Supplier<U> uncheck() {
        return () -> {
            try {
                return supply();
            } catch (Throwable t) {
                IOHelper.sneakyThrow(t);
                return null;
            }
        };
    }

    public static <U> Supplier<U> uncheck(ExceptionalSupplier<U> supplier) {
        return supplier.uncheck();
    }
}
