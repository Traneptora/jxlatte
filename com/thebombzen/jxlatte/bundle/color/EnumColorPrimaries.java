package com.thebombzen.jxlatte.bundle.color;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum EnumColorPrimaries {
    SRGB(1, new CIEXY(0.639998686f, 0.330010138f), new CIEXY(0.300003784f, 0.600003357f), new CIEXY(0.150002046f, 0.059997204f)),
    CUSTOM(2, null, null, null),
    BT2100(9, new CIEXY(0.708f, 0.292f), new CIEXY(0.170f, 0.797f), new CIEXY(0.131f, 0.046f)),
    P3(11, new CIEXY(0.680f, 0.320f), new CIEXY(0.265f, 0.690f), new CIEXY(0.150f, 0.060f));

    private static Map<Integer, EnumColorPrimaries> map = new HashMap<>();

    static {
        Arrays.asList(EnumColorPrimaries.values()).stream().forEach(e -> {
            map.put(e.index, e);
        });
    }

    public static EnumColorPrimaries getForIndex(int index) {
        return map.get(index);
    }

    public final int index;
    public final CIEXY red;
    public final CIEXY green;
    public final CIEXY blue;

    private EnumColorPrimaries(int index, CIEXY red, CIEXY green, CIEXY blue) {
        this.index = index;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }
}
