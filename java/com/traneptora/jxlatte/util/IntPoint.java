package com.traneptora.jxlatte.util;

import java.util.stream.Stream;

/**
 * A mutable pair of coordinates
 */
public class IntPoint {
    public final int x;
    public final int y;

    public static final IntPoint ZERO = new IntPoint();
    public static final IntPoint ONE = new IntPoint(1, 1);

    public static IntPoint coordinates(int index, int rowStride) {
        return new IntPoint(index % rowStride, index / rowStride);
    }

    public static IntPoint sizeOf(float[][] array) {
        return array.length == 0 ? new IntPoint() : new IntPoint(array[0].length, array.length);
    }

    public static IntPoint sizeOf(int[][] array) {
        return array.length == 0 ? new IntPoint() : new IntPoint(array[0].length, array.length);
    }

    public static <T> IntPoint sizeOf(T[][] array) {
        return array.length == 0 ? new IntPoint() : new IntPoint(array[0].length, array.length);
    }

    public static IntPoint[] sizeOf(float[][][] array) {
        return Stream.of(array).map(IntPoint::sizeOf).toArray(IntPoint[]::new);
    }

    public static IntPoint[] sizeOf(int[][][] array) {
        return Stream.of(array).map(IntPoint::sizeOf).toArray(IntPoint[]::new);
    }

    public static <T> IntPoint[] sizeOf(T[][][] array) {
        return Stream.of(array).map(IntPoint::sizeOf).toArray(IntPoint[]::new);
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
        final int x = this.x << hshift;
        final int y = this.y << vshift;
        return new IntPoint(x, y);
    }

    public IntPoint shiftLeft(int shift) {
        return shiftLeft(shift, shift);
    }

    public IntPoint shiftLeft(IntPoint shift) {
        return shiftLeft(shift.x, shift.y);
    }

    public IntPoint shiftRight(int hshift, int vshift) {
        final int x = this.x >> hshift;
        final int y = this.y >> vshift;
        return new IntPoint(x, y);
    }

    public IntPoint shiftRight(int shift) {
        return shiftRight(shift, shift);
    }

    public IntPoint shiftRight(IntPoint shift) {
        return shiftRight(shift.x, shift.y);
    }

    public int unwrapCoord(int rowStride) {
        return y * rowStride + x;
    }

    public IntPoint min(IntPoint p) {
        return new IntPoint(Math.min(x, p.x), Math.min(y, p.y));
    }

    public IntPoint max(IntPoint p) {
        return new IntPoint(Math.max(x, p.x), Math.max(y, p.y));
    }

    public IntPoint mirrorCoordinate(IntPoint size) {
        return new IntPoint(MathHelper.mirrorCoordinate(x, size.x), MathHelper.mirrorCoordinate(y, size.y));
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
