package com.thebombzen.jxlatte;

import java.util.function.Supplier;

import com.thebombzen.jxlatte.io.IOHelper;

@FunctionalInterface
public interface ExceptionalSupplier<U> {
    
    public U supplyExceptionally() throws Throwable;

    public default Supplier<U> uncheck() {
        return uncheck(this);
    }

    public static <U> Supplier<U> uncheck(ExceptionalSupplier<U> supplier) {
        return () -> {
            try {
                return supplier.supplyExceptionally();
            } catch (Throwable t) {
                IOHelper.sneakyThrow(t);
                return null;
            }
        };
    }
}
