package com.thebombzen.jxlatte.util.functional;

import java.util.function.Function;

@FunctionalInterface
public interface ExceptionalFunction<C, U> extends Function<C, U> {
    public U applyExceptionally(C c) throws Throwable;

    public default U apply(C c) {
        try {
            return applyExceptionally(c);
        } catch (Throwable ex) {
            return FunctionalHelper.sneakyThrow(ex);
        }
    }

    public static <D, R> Function<D, R> of(ExceptionalFunction<D, R> func) {
        return func;
    }
}
