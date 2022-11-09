package com.thebombzen.jxlatte.util.functional;

@FunctionalInterface
public interface ExceptionalIntBiConsumer {
    public void consume(int x, int y) throws Throwable;
}
