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

    public Point abs() {
        return new Point(Math.abs(y), Math.abs(x));
    }

    /**
     * Gives the closest point to this one within the bounds of the rectangle
     */
    public Point withinBounds(Rectangle bounds) {
        Point corner = bounds.computeLowerCorner();
        int y = MathHelper.clamp(this.y, bounds.origin.y, corner.y);
        int x = MathHelper.clamp(this.x, bounds.origin.x, corner.x);
        return new Point(y, x);
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
