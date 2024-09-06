package com.traneptora.jxlatte.util;

import java.util.Objects;

public class Point {
    public int y;
    public int x;

    public Point(int y, int x) {
        this.y = y;
        this.x = x;
    }

    public Point() {
        this(0, 0);
    }

    public Point(Point p) {
        this(p.y, p.x);
    }

    @Override
    public int hashCode() {
        return Objects.hash(y, x);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Point other = (Point) obj;
        return y == other.y && x == other.x;
    }

    @Override
    public String toString() {
        return String.format("(y=%d, x=%d)", y, x);
    }
}
