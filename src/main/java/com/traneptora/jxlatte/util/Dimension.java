package com.traneptora.jxlatte.util;

import java.util.Objects;

public class Dimension {
    public int height;
    public int width;

    public Dimension() {
        this(0, 0);
    }

    public Dimension(int height, int width) {
        this.height = height;
        this.width = width;
    }

    public Dimension(Dimension d) {
        this(d.height, d.width);
    }

    public Dimension asTransposed() {
        return new Dimension(width, height);
    }

    @Override
    public int hashCode() {
        return Objects.hash(height, width);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Dimension other = (Dimension) obj;
        return height == other.height && width == other.width;
    }

    @Override
    public String toString() {
        return String.format("(h=%d, w=%d)", height, width);
    }
}
