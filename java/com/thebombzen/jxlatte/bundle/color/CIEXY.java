package com.thebombzen.jxlatte.bundle.color;

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

    @Override
    public String toString() {
        return String.format("CIEXY [x=%s, y=%s]", x, y);
    }
}
