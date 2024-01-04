package com.traneptora.jxlatte.util.functional;

@FunctionalInterface
public interface ExceptionalIntConsumer {
    public void consume(int a) throws Throwable;
}
