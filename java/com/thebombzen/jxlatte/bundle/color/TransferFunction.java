package com.thebombzen.jxlatte.bundle.color;

import java.util.function.DoubleUnaryOperator;

public interface TransferFunction {
    public static final int BT709 = 1 + (1 << 24);
    public static final int UNKNOWN = 2 + (1 << 24);
    public static final int LINEAR = 8 + (1 << 24);
    public static final int SRGB = 13 + (1 << 24);
    public static final int PQ = 16 + (1 << 24);
    public static final int DCI = 17 + (1 << 24);
    public static final int HLG = 18 + (1 << 24);

    public static boolean validate(int transfer) {
        if (transfer < 0)
            return false;
        else if (transfer <= 10_000_000)
            return true;
        else if (transfer < (1 << 24))
            return false;
        else
            return transfer == BT709
                || transfer == UNKNOWN
                || transfer == LINEAR
                || transfer == SRGB
                || transfer == PQ
                || transfer == DCI
                || transfer == HLG;
    }

    public static DoubleUnaryOperator getTransferFunction(int transfer) {
        switch (transfer) {
            case LINEAR:
                return f -> f;
            case SRGB:
                return f -> {
                    if (f <= 0.00313066844250063D)
                        return f * 12.92D;
                    else
                        return 1.055D * Math.pow(f, 1D/2.4D) - 0.055D;
                };
            case BT709:
            case PQ:
            case DCI:
            case HLG:
                throw new UnsupportedOperationException("Not yet implemented");
        }
        if (transfer < (1 << 24)) {
            double gamma = 1e7D / transfer;
            return f -> Math.pow(f, gamma);
        }
        return null;
    }
}
