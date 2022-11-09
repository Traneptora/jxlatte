package com.thebombzen.jxlatte.util.functional;

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

    public default <T> ExceptionalSupplier<T> supply() {
        return () -> {
            run();
            return null;
        };
    }
}
