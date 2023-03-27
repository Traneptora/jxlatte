package com.thebombzen.jxlatte.util.functional;

@FunctionalInterface
public interface ExceptionalIntTriConsumer {
    public void consumeExceptionally(int c, int x, int y) throws Throwable;

    public default void consume(int c, int x, int y) {
        try {
            consumeExceptionally(c, x, y);
        } catch (Throwable ex) {
            FunctionalHelper.sneakyThrow(ex);
        }
    }
}
