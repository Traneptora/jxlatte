package com.thebombzen.jxlatte.util;

import java.util.stream.Stream;

import com.thebombzen.jxlatte.util.functional.ExceptionalIntBiConsumer;
import com.thebombzen.jxlatte.util.functional.ExceptionalIntTriConsumer;

/**
 * A mutable pair of coordinates
 */
public class IntPoint {
    public int x;
    public int y;

    public static final IntPoint ZERO = new IntPoint();
    public static final IntPoint ONE = new IntPoint(1, 1);

    public static IntPoint coordinates(int index, int rowStride) {
        return new IntPoint(index % rowStride, index / rowStride);
    }

    public static IntPoint min(IntPoint p1, IntPoint p2) {
        return new IntPoint(Math.min(p1.x, p2.x), Math.min(p1.y, p2.y));
    }

    public static IntPoint max(IntPoint p1, IntPoint p2) {
        return new IntPoint(Math.max(p1.x, p2.x), Math.max(p1.y, p2.y));
    }

    public static double get(double[][] array, IntPoint pos) {
        return array[pos.y][pos.x];
    }

    public static int get(int[][] array, IntPoint pos) {
        return array[pos.y][pos.x];
    }

    public static <T> T get(T[][] array, IntPoint pos) {
        return array[pos.y][pos.x];
    }

    public static void set(double[][] array, IntPoint pos, double value) {
        array[pos.y][pos.x] = value;
    }

    public static void set(int[][] array, IntPoint pos, int value) {
        array[pos.y][pos.x] = value;
    }

    public static <T> void set(T[][] array, IntPoint pos, T value) {
        array[pos.y][pos.x] = value;
    }

    public static IntPoint sizeOf(double[][] array) {
        return array.length == 0 ? new IntPoint() : new IntPoint(array[0].length, array.length);
    }

    public static IntPoint sizeOf(int[][] array) {
        return array.length == 0 ? new IntPoint() : new IntPoint(array[0].length, array.length);
    }

    public static <T> IntPoint sizeOf(T[][] array) {
        return array.length == 0 ? new IntPoint() : new IntPoint(array[0].length, array.length);
    }

    public static IntPoint[] sizeOf(double[][][] array) {
        return Stream.of(array).map(IntPoint::sizeOf).toArray(IntPoint[]::new);
    }

    public static IntPoint[] sizeOf(int[][][] array) {
        return Stream.of(array).map(IntPoint::sizeOf).toArray(IntPoint[]::new);
    }

    public static <T> IntPoint[] sizeOf(T[][][] array) {
        return Stream.of(array).map(IntPoint::sizeOf).toArray(IntPoint[]::new);
    }

    public static void iterate(int c, IntPoint[] size, ExceptionalIntTriConsumer func) {
        for (int i = 0; i < c; i++) {
            final int j = i;
            iterate(size[i], (x, y) -> func.consume(j, x, y));
        }
    }

    public static void iterate(int c, IntPoint size, ExceptionalIntTriConsumer func) {
        for (int i = 0; i < c; i++) {
            final int j = i;
            iterate(size, (x, y) -> func.consume(j, x, y));
        }
    }

    public static void iterate(IntPoint size, ExceptionalIntBiConsumer func) {
        iterate(size.x, size.y, func);
    }

    public static void iterate(int width, int height, ExceptionalIntBiConsumer func) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                func.consume(x, y);
            }
        }
    }

    public static void parallelIterate(int c, IntPoint[] size, ExceptionalIntTriConsumer func) {
        TaskList<Void> tasks = new TaskList<>();
        for (int i = 0; i < c; i++) {
            final int j = i;
            parallelIterate(tasks, size[i], (x, y) -> func.consume(j, x, y));
        }
        tasks.collect();
    }

    public static void parallelIterate(int c, IntPoint size, ExceptionalIntTriConsumer func) {
        TaskList<Void> tasks = new TaskList<>();
        for (int i = 0; i < c; i++) {
            final int j = i;
            parallelIterate(tasks, size, (x, y) -> func.consume(j, x, y));
        }
        tasks.collect();
    }

    public static void parallelIterate(IntPoint size, ExceptionalIntBiConsumer func) {
        TaskList<Void> tasks = new TaskList<>();
        parallelIterate(tasks, size, func);
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

    public IntPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public IntPoint(int dim) {
        this(dim, dim);
    }

    public IntPoint(IntPoint p) {
        this(p.x, p.y);
    }

    public IntPoint() {
        this(0, 0);
    }

    public IntPoint negate() {
        return new IntPoint(-x, -y);
    }

    public IntPoint plus(IntPoint p) {
        return new IntPoint(x + p.x, y + p.y);
    }

    public IntPoint minus(IntPoint p) {
        return new IntPoint(x - p.x, y - p.y);
    }

    public IntPoint times(double factor) {
        return new IntPoint((int)(x * factor), (int)(y * factor));
    }

    public IntPoint times(IntPoint p) {
        return new IntPoint(x * p.x, y * p.y);
    }

    public IntPoint times(int factor) {
        return new IntPoint(x * factor, y * factor);
    }

    public IntPoint divide(int factor) {
        return new IntPoint(x / factor, y / factor);
    }

    public IntPoint ceilDiv(int factor) {
        return new IntPoint(MathHelper.ceilDiv(x, factor), MathHelper.ceilDiv(y, factor));
    }

    public IntPoint ceilDiv(IntPoint p) {
        return new IntPoint(MathHelper.ceilDiv(x, p.x), MathHelper.ceilDiv(y, p.y));
    }

    public IntPoint transpose() {
        return new IntPoint(y, x);
    }

    public IntPoint shiftLeft(int hshift, int vshift) {
        int x = hshift < 0 ? this.x >> -hshift : this.x << hshift;
        int y = vshift < 0 ? this.y >> -vshift : this.y << vshift;
        return new IntPoint(x, y);
    }

    public IntPoint shiftLeft(int shift) {
        return shiftLeft(shift, shift);
    }

    public IntPoint shiftLeft(IntPoint shift) {
        return shiftLeft(shift.x, shift.y);
    }

    public IntPoint shiftRight(int hshift, int vshift) {
        return shiftLeft(-hshift, -vshift);
    }

    public IntPoint shiftRight(int shift) {
        return shiftLeft(-shift);
    }

    public IntPoint shiftRight(IntPoint shift) {
        return shiftLeft(shift.negate());
    }

    public int unwrapCoord(int rowStride) {
        return y * rowStride + x;
    }

    @Override
    public int hashCode() {
        return ((x << 16) | (x >>> 16)) ^ y;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        IntPoint other = (IntPoint) obj;
        return x == other.x && y == other.y;
    }

    @Override
    public String toString() {
        return String.format("[x=%s, y=%s]", x, y);
    }
}
