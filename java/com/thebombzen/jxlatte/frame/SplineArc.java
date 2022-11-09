package com.thebombzen.jxlatte.frame;

import com.thebombzen.jxlatte.util.DoublePoint;

public class SplineArc {
    public final DoublePoint location;
    public final double arcLength;

    public SplineArc(DoublePoint location, double arcLength) {
        this.location = location;
        this.arcLength = arcLength;
    }

}
