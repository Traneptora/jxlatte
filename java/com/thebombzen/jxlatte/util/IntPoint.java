package com.thebombzen.jxlatte.util;

import java.util.Objects;

public class IntPoint {
    public int x;
    public int y;

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

    public IntPoint plus(IntPoint p) {
        return new IntPoint(x + p.x, y + p.y);
    }

    public IntPoint minus(IntPoint p) {
        return new IntPoint(x - p.x, y - p.y);
    }

    public IntPoint times(double factor) {
        return new IntPoint((int)(x * factor), (int)(y * factor));
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
