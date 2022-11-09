package com.thebombzen.jxlatte.util;

public class DoublePoint {
    public double x;
    public double y;

    public DoublePoint(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public DoublePoint(DoublePoint p) {
        this(p.x, p.y);
    }

    public DoublePoint(IntPoint p) {
        this(p.x, p.y);
    }

    public DoublePoint() {
        this(0, 0);
    }

    public DoublePoint plus(DoublePoint p) {
        return new DoublePoint(x + p.x, y + p.y);
    }

    public DoublePoint minus(DoublePoint p) {
        return new DoublePoint(x - p.x, y - p.y);
    }

    public DoublePoint times(double factor) {
        return new DoublePoint(x * factor, y * factor);
    }

    public void plusEquals(DoublePoint p) {
        this.x += p.x;
        this.y += p.y;
    }

    public void minusEquals(DoublePoint p) {
        this.x -= p.x;
        this.y -= p.y;
    }

    public void timesEquals(double factor) {
        this.x *= factor;
        this.y *= factor;
    }

    public double normSquared() {
        return x * x + y * y;
    }

    public double norm() {
        return Math.sqrt(normSquared());
    }
}
