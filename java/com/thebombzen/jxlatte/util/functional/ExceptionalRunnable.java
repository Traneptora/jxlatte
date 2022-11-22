package com.thebombzen.jxlatte.util.functional;

@FunctionalInterface
public interface ExceptionalRunnable extends Runnable {
    public void runExceptionally() throws Throwable;

    @Override
    public default void run() {
        try {
            runExceptionally();
        } catch (Throwable ex) {
            FunctionalHelper.sneakyThrow(ex);
        }
    }

    public static Runnable of(ExceptionalRunnable r) {
        return r;
    }

    public default <T> ExceptionalSupplier<T> supply() {
        return () -> {
            run();
            return null;
        };
    }
}
