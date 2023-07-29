package com.thebombzen.jxlatte.util;

import java.util.concurrent.ExecutorService;

public class FlowHelper {

    public static Iterable<IntPoint> range2D(int startX, int startY, int endX, int endY) {
        return new Range2DIterator(startX, startY, endX, endY);
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
        return new RangeIterator(startIndex, endIndex);
    }

    private ExecutorService threadPool;

    public FlowHelper(ExecutorService service) {
        threadPool = service;
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }
}
