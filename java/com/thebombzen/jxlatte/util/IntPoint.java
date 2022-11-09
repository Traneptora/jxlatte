package com.thebombzen.jxlatte.util;

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
}

