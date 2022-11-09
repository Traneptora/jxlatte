package com.thebombzen.jxlatte.util;

@FunctionalInterface
public interface ExceptionalIntBiConsumer {
    public void consume(int x, int y) throws Throwable;
}
