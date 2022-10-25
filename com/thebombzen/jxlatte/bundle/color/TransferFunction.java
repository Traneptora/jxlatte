package com.thebombzen.jxlatte.bundle.color;

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

    public static FloatFunction getTransferFunction(int transfer) {
        switch (transfer) {
            case LINEAR:
                return f -> f;
            case SRGB:
                return f -> {
                    if (f <= 0.00313066844250063f)
                        return f * 12.92f;
                    else
                        return 1.055f * (float)Math.pow(f, 1d/2.4d) - 0.055f;
                };
            case BT709:
            case PQ:
            case DCI:
            case HLG:
                throw new UnsupportedOperationException("Not yet implemented");
        }
        if (transfer < (1 << 24)) {
            float gamma = 1e7f / (float)transfer;
            return f -> (float)Math.pow(f, gamma);
        }
        return null;
    }
}
