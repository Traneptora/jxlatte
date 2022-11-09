package com.thebombzen.jxlatte.util.functional;

import java.util.function.Function;

@FunctionalInterface
public interface ExceptionalFunction<C, U> {
    public U applyExceptionally(C c) throws Throwable;

    public default Function<C, U> uncheck() {
        return (c) -> {
            try {
                return applyExceptionally(c);
            } catch (Throwable ex) {
                return FunctionalHelper.sneakyThrow(ex);
            }
        };
    }
}
