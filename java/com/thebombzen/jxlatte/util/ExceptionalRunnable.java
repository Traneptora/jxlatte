package com.thebombzen.jxlatte.util;

@FunctionalInterface
public interface ExceptionalRunnable {
    public void run() throws Throwable;

    public default Runnable uncheck() {
        return () -> {
            try {
                run();
            } catch (Throwable ex) {
                FunctionalHelper.sneakyThrow(ex);
            }
        };
    }
}
