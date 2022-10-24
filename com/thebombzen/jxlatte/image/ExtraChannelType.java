package com.thebombzen.jxlatte.image;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum ExtraChannelType {
    ALPHA(0),
    DEPTH(1),
    SPOT_COLOR(2),
    SELECTION_MASK(3),
    CMYK_BLACK(4),
    COLOR_FILTER_ARRAY(5),
    THERMAL(6),
    NON_OPTIONAL(7),
    OPTIONAL(8);

    private static Map<Integer, ExtraChannelType> map = new HashMap<>();

    static {
        Arrays.asList(ExtraChannelType.values()).stream().forEach(e -> {
            map.put(e.index, e);
        });
    }

    public static ExtraChannelType getForIndex(int index) {
        return map.get(Integer.valueOf(index));
    }

    public final int index;

    private ExtraChannelType(int index) {
        this.index = index;
    }
}
