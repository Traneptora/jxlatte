package com.thebombzen.jxlatte.bundle.color;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum EnumRenderingIntent {
    PERCEPTUAL(0),
    RELATIVE(1),
    SATURATION(2),
    ABSOLUTE(3);

    private static Map<Integer, EnumRenderingIntent> map = new HashMap<>();

    static {
        Arrays.asList(EnumRenderingIntent.values()).stream().forEach(e -> {
            map.put(e.index, e);
        });
    }

    public static EnumRenderingIntent getForIndex(int index) {
        return map.get(index);
    }

    public final int index;

    private EnumRenderingIntent(int index) {
        this.index = index;
    }
}
