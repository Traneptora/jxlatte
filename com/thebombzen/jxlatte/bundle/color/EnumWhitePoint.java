package com.thebombzen.jxlatte.bundle.color;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum EnumWhitePoint {
    D65(1, new CIEXY(0.3127f, 0.3290f)),
    CUSTOM(2, null),
    E(10, new CIEXY(1f/3f, 1f/3f)),
    DCI(11, new CIEXY(0.314f, 0.351f));

    private static Map<Integer, EnumWhitePoint> map = new HashMap<>();

    static {
        Arrays.asList(EnumWhitePoint.values()).stream().forEach(e -> {
            map.put(e.index, e);
        });
    }

    public static EnumWhitePoint getForIndex(int index) {
        return map.get(index);
    }

    public final CIEXY xy;
    public final int index;

    private EnumWhitePoint(int index, CIEXY xy) {
        this.index = index;
        this.xy = xy;
    }
}
