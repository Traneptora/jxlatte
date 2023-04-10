package com.thebombzen.jxlatte.util;

import java.util.concurrent.ExecutorService;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.thebombzen.jxlatte.util.functional.ExceptionalConsumer;
import com.thebombzen.jxlatte.util.functional.ExceptionalIntBiConsumer;
import com.thebombzen.jxlatte.util.functional.ExceptionalIntTriConsumer;

public class FlowHelper {

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

    public static Iterable<IntPoint> range2D(IntPoint origin, IntPoint lowerRight) {
        return range2D(origin.x, origin.y, lowerRight.x, lowerRight.y);
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

    private ExecutorService threadPool;

    public FlowHelper(ExecutorService service) {
        threadPool = service;
    }

    public void parallelIterate(int c, IntPoint[] size, ExceptionalIntTriConsumer func) {
        TaskList<Void> tasks = new TaskList<>(threadPool);
        for (final int i : range(c))
            parallelIterate(tasks, size[i], (x, y) -> func.consume(i, x, y));
        tasks.collect();
    }

    public void parallelIterate(int c, IntPoint size, ExceptionalIntTriConsumer func) {
        TaskList<Void> tasks = new TaskList<>(threadPool);
        for (final int i : range(c))
            parallelIterate(tasks, size, (x, y) -> func.consume(i, x, y));
        tasks.collect();
    }

    public void parallelIterate(IntPoint size, ExceptionalIntBiConsumer func) {
        TaskList<Void> tasks = new TaskList<>(threadPool);
        parallelIterate(tasks, size, func);
        tasks.collect();
    }

    public void parallelIterate(IntPoint size, ExceptionalConsumer<? super IntPoint> func) {
        TaskList<Void> tasks = new TaskList<>(threadPool);
        parallelIterate(tasks, size, func);
        tasks.collect();
    }

    private void parallelIterate(TaskList<?> tasks, IntPoint size, ExceptionalIntBiConsumer func) {
        for (final int y : range(size.y))
            tasks.submit(() -> IntStream.range(0, size.x).forEach(x -> func.consume(x, y)));
    }

    private void parallelIterate(TaskList<?> tasks, IntPoint size, ExceptionalConsumer<? super IntPoint> func) {
        for (final int y : range(size.y))
            tasks.submit(() -> IntStream.range(0, size.x).forEach(x -> func.accept(new IntPoint(x, y))));
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }
}
