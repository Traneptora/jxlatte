package com.thebombzen.jxlatte.bundle.color;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum EnumTransferFunction {
    BT709(1, f -> {
        throw new UnsupportedOperationException("Not yet implemented");
    }),
    UNKNOWN(2, null),
    LINEAR(8, f -> f),
    SRGB(13, f -> {
        if (f <= 0.00313066844250063f)
            return f * 12.92f;
        else
            return 1.055f * (float)Math.pow(f, 1d/2.4d) - 0.055f;
    }),
    PQ(16, f -> {
        throw new UnsupportedOperationException("Not yet implemented");
    }),
    DCI(17, f -> {
        throw new UnsupportedOperationException("Not yet implemented");
    }),
    HLG(18, f -> {
        throw new UnsupportedOperationException("Not yet implemented");
    });

    private static Map<Integer, EnumTransferFunction> map = new HashMap<>();

    static {
        Arrays.asList(EnumTransferFunction.values()).stream().forEach(e -> {
            map.put(e.index, e);
        });
    }

    public static EnumTransferFunction getForIndex(int index) {
        return map.get(index);
    }

    public final int index;
    public final FloatFunction transferFunction;

    private EnumTransferFunction(int index, FloatFunction transferFunction) {
        this.index = index;
        this.transferFunction = transferFunction;
    }
}
