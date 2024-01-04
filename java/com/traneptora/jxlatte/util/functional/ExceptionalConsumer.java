package com.thebombzen.jxlatte.util.functional;

import java.util.function.Consumer;

@FunctionalInterface
public interface ExceptionalConsumer<T> extends Consumer<T> {
    public void consumeExceptionally(T t) throws Throwable;

    @Override
    public default void accept(T t) {
        try {
            consumeExceptionally(t);
        } catch (Throwable ex) {
            FunctionalHelper.sneakyThrow(ex);
        }
    }
}
