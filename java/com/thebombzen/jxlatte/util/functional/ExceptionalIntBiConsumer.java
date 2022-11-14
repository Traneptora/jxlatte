package com.thebombzen.jxlatte.util.functional;

@FunctionalInterface
public interface ExceptionalIntBiConsumer {
    public void consumeExceptionally(int x, int y) throws Throwable;

    public default void consume(int x, int y) {
        try {
            consumeExceptionally(x, y);
        } catch (Throwable ex) {
            FunctionalHelper.sneakyThrow(ex);
        }
    }
}
