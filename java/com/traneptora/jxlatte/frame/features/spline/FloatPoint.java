package com.traneptora.jxlatte.frame.features.spline;

import com.traneptora.jxlatte.util.IntPoint;

public class FloatPoint {
    public float x;
    public float y;

    public FloatPoint(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public FloatPoint(FloatPoint p) {
        this(p.x, p.y);
    }

    public FloatPoint(IntPoint p) {
        this(p.x, p.y);
    }

    public FloatPoint() {
        this(0, 0);
    }

    public FloatPoint plus(FloatPoint p) {
        return new FloatPoint(x + p.x, y + p.y);
    }

    public FloatPoint minus(FloatPoint p) {
        return new FloatPoint(x - p.x, y - p.y);
    }

    public FloatPoint times(float factor) {
        return new FloatPoint(x * factor, y * factor);
    }

    public float normSquared() {
        return x * x + y * y;
    }

    public float norm() {
        return (float)Math.sqrt(normSquared());
    }
}
