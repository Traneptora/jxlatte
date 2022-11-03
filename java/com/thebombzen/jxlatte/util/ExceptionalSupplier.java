package com.thebombzen.jxlatte.util;

import java.util.function.Supplier;

@FunctionalInterface
public interface ExceptionalSupplier<U> {
    
    public U supply() throws Throwable;

    public default Supplier<U> uncheck() {
        return () -> {
            try {
                return supply();
            } catch (Throwable t) {
                return FunctionalHelper.sneakyThrow(t);
            }
        };
    }
}
