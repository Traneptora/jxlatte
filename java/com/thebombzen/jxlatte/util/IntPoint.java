package com.thebombzen.jxlatte.util;

import java.util.Objects;

/**
 * A mutable pair of coordinates
 */
public class IntPoint {
    public int x;
    public int y;

    public static IntPoint coordinates(int index, int rowStride) {
        return new IntPoint(index % rowStride, index / rowStride);
    }

    public IntPoint(int x, int y) {
        this.x = x;
        this.y = y;
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

    public void negateEquals() {
        x = -x;
        y = -y;
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

    public IntPoint times(int factor) {
        return new IntPoint(x * factor, y * factor);
    }

    public void timesEquals(int factor) {
        this.x *= factor;
        this.y *= factor;
    }

    public IntPoint divide(int factor) {
        return new IntPoint(x / factor, y / factor);
    }

    public void divideEquals(int factor) {
        this.x /= factor;
        this.y /= factor;
    }

    public void plusEquals(IntPoint p) {
        this.x += p.x;
        this.y += p.y;
    }

    public void minusEquals(IntPoint p) {
        this.x -= p.x;
        this.y -= p.y;
    }

    public void timesEquals(double factor) {
        this.x = (int)(this.x * factor);
        this.y = (int)(this.y * factor);
    }

    public IntPoint transpose() {
        return new IntPoint(this.y, this.x);
    }

    public void transposeEquals() {
        int tmp = this.y;
        this.y = this.x;
        this.x = tmp;
    }

    public IntPoint shift(int hshift, int vshift) {
        int x = hshift < 0 ? this.x >> -hshift : this.x << hshift;
        int y = vshift < 0 ? this.y >> -vshift : this.y << vshift;
        return new IntPoint(x, y);
    }

    public IntPoint shift(int shift) {
        return shift(shift, shift);
    }

    public IntPoint shift(IntPoint shift) {
        return shift(shift.x, shift.y);
    }

    public void shiftEquals(int hshift, int vshift) {
        this.x = hshift < 0 ? this.x >> -hshift : this.x << hshift;
        this.y = vshift < 0 ? this.y >> -vshift : this.y << vshift;
    }

    public void shiftEquals(int shift) {
        shiftEquals(shift, shift);
    }

    public void shiftEquals(IntPoint shift) {
        shiftEquals(shift.x, shift.y);
    }

    public int unwrapCoord(int rowStride) {
        return y * rowStride + x;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
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
