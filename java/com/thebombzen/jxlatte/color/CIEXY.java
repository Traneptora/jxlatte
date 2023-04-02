package com.thebombzen.jxlatte.color;

import java.util.Objects;

public class CIEXY {
    public final float x;
    public final float y;

    public CIEXY(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public CIEXY(CIEXY xy) {
        this(xy.x, xy.y);
    }

    public boolean matches(CIEXY xy) {
        return Math.abs(x - xy.x) + Math.abs(y - xy.y) < 1e-4D;
    }

    public static boolean matches(CIEXY a, CIEXY b) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        return a.matches(b);
    }

    @Override
    public String toString() {
        return String.format("CIEXY [x=%s, y=%s]", x, y);
    }

    public boolean equals(Object another) {
        if (another == null || !another.getClass().equals(this.getClass()))
            return false;
        CIEXY other = (CIEXY)another;
        return Float.valueOf(x).equals(other.x) && Float.valueOf(y).equals(other.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Float.hashCode(x), Float.hashCode(y));
    }
}
