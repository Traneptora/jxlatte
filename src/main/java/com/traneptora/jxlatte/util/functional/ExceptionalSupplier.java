package com.traneptora.jxlatte.util.functional;

import java.util.function.Supplier;

@FunctionalInterface
public interface ExceptionalSupplier<U> extends Supplier<U> {
    
    public U supplyExceptionally() throws Throwable;

    public default U get() {
        try {
            return supplyExceptionally();
        } catch (Throwable ex) {
            return FunctionalHelper.sneakyThrow(ex);
        }
    }
}
