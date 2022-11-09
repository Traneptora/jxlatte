package com.thebombzen.jxlatte.util;

@FunctionalInterface
public interface ExceptionalIntConsumer {
    public void consume(int a) throws Throwable;
}
