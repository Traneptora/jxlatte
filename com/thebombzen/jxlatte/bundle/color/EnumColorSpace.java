package com.thebombzen.jxlatte.bundle.color;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum EnumColorSpace {
    RGB(0),
    GRAY(1),
    XYB(2),
    UNKNOWN(3);

    private static Map<Integer, EnumColorSpace> map = new HashMap<>();

    static {
        Arrays.asList(EnumColorSpace.values()).stream().forEach(e -> {
            map.put(e.index, e);
        });
    }

    public static EnumColorSpace getForIndex(int index) {
        return map.get(index);
    }

    public final int index;

    private EnumColorSpace(int index) {
        this.index = index;
    }
}
