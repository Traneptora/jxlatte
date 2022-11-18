package com.thebombzen.jxlatte.util;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.thebombzen.jxlatte.util.functional.ExceptionalConsumer;
import com.thebombzen.jxlatte.util.functional.ExceptionalIntBiConsumer;
import com.thebombzen.jxlatte.util.functional.ExceptionalIntTriConsumer;

public final class FlowHelper {

    public static void parallelIterate(int c, IntPoint[] size, ExceptionalIntTriConsumer func) {
        TaskList<Void> tasks = new TaskList<>();
        for (int i = 0; i < c; i++) {
            final int j = i;
            FlowHelper.parallelIterate(tasks, size[i], (x, y) -> func.consume(j, x, y));
        }
        tasks.collect();
    }

    public static void parallelIterate(int c, IntPoint size, ExceptionalIntTriConsumer func) {
        TaskList<Void> tasks = new TaskList<>();
        for (int i = 0; i < c; i++) {
            final int j = i;
            FlowHelper.parallelIterate(tasks, size, (x, y) -> func.consume(j, x, y));
        }
        tasks.collect();
    }

    public static void parallelIterate(IntPoint size, ExceptionalIntBiConsumer func) {
        TaskList<Void> tasks = new TaskList<>();
        FlowHelper.parallelIterate(tasks, size, func);
        tasks.collect();
    }

    public static void parallelIterate(IntPoint size, ExceptionalConsumer<? super IntPoint> func) {
        TaskList<Void> tasks = new TaskList<>();
        FlowHelper.parallelIterate(tasks, size, func);
        tasks.collect();
    }

    private static void parallelIterate(TaskList<?> tasks, IntPoint size, ExceptionalIntBiConsumer func) {
        for (int y0 = 0; y0 < size.y; y0++) {
            final int y = y0;
            tasks.submit(() -> {
                for (int x = 0; x < size.x; x++) {
                    func.consume(x, y);
                }
            });
        }
    }

    private static void parallelIterate(TaskList<?> tasks, IntPoint size, ExceptionalConsumer<? super IntPoint> func) {
        for (int y0 = 0; y0 < size.y; y0++) {
            final int y = y0;
            tasks.submit(() -> {
                for (int x = 0; x < size.x; x++) {
                    func.accept(new IntPoint(x, y));
                }
            });
        }
    }

    public static Iterable<IntPoint> range2D(int startX, int startY, int endX, int endY) {
        return new IteratorIterable<>(IntStream.range(startY, endY).mapToObj(
            y -> IntStream.range(startX, endX).mapToObj(x -> new IntPoint(x, y)))
            .reduce(Stream::concat).orElse(Stream.empty()).iterator());
    }

    public static Iterable<IntPoint> range2D(int width, int height) {
        return range2D(0, 0, width, height);
    }

    public static Iterable<IntPoint> range2D(IntPoint size) {
        return range2D(0, 0, size.x, size.y);
    }

    public static Iterable<IntPoint> range2D(IntPoint origin, IntPoint size) {
        return range2D(origin.x, origin.y, size.x, size.y);
    }

    /**
     * Useful to prevent final modification of entries in for loops
     */
    public static Iterable<Integer> range(int size) {
        return range(0, size);
    }

    public static Iterable<Integer> range(int startIndex, int endIndex) {
        return new IteratorIterable<Integer>(IntStream.range(startIndex, endIndex).iterator());
    }

    private FlowHelper() {}
}
