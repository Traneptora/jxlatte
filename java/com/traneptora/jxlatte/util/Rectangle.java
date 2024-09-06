package com.traneptora.jxlatte.util;

import java.util.Objects;

public class Rectangle {
    public Point origin;
    public Dimension size;

    public Rectangle() {
        this(0, 0, 0, 0);
    }

    public Rectangle(int y, int x, int height, int width) {
        this.origin = new Point(y, x);
        this.size = new Dimension(height, width);
    }

    public Rectangle(Point origin, Dimension size) {
        this(origin.y, origin.x, size.height, size.width);
    }

    public Rectangle(Rectangle r) {
        this(r.origin, r.size);
    }

    public Point computeLowerCorner() {
        return new Point(origin.y + size.height, origin.x + size.width);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, size);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Rectangle other = (Rectangle) obj;
        return Objects.equals(origin, other.origin) && Objects.equals(size, other.size);
    }

    @Override
    public String toString() {
        return String.format("Rect(origin=%s, size=%s)", origin, size);
    }
}
