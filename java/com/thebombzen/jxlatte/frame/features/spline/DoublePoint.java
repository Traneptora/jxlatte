package com.thebombzen.jxlatte.frame.features.spline;

import com.thebombzen.jxlatte.util.IntPoint;

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

    public double normSquared() {
        return x * x + y * y;
    }

    public double norm() {
        return Math.sqrt(normSquared());
    }
}
