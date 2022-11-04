package com.thebombzen.jxlatte.bundle.color;

import java.util.function.DoubleUnaryOperator;

import com.thebombzen.jxlatte.util.MathHelper;

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

    public static DoubleUnaryOperator getInverseTransferFunction(int transfer) {
        switch (transfer) {
            case LINEAR:
                return DoubleUnaryOperator.identity();
            case SRGB:
                return f -> {
                    if (f <= 0.0404482362771082D)
                        return f * 0.07739938080495357D;
                    else
                        return Math.pow((f + 0.055D) * 0.9478672985781991D, 2.4D);
                };
            case BT709:
                return f -> {
                    if (f <= 0.081242858298635133011D)
                        return f * 0.22222222222222222222D;
                    else
                        return Math.pow((f + 0.0992968268094429403D) * 0.90967241568627260377D, 2.2222222222222222222D);
                };
            case PQ:
                return f -> {
                    double d = MathHelper.signedPow(f, 0.012683313515655965121D);
                    return MathHelper.signedPow((d - 0.8359375D) / (18.8515625D + 18.6875D * d), 6.2725880551301684533D);
                };
            case DCI:
            case HLG:
                throw new UnsupportedOperationException("Not yet implemented");
        }

        if (transfer < (1 << 24)) {
            double gamma = transfer * 1e-7D;
            return f -> MathHelper.signedPow(f, gamma);
        }

        throw new IllegalArgumentException("Invalid transfer function");
    }

    public static DoubleUnaryOperator getTransferFunction(int transfer) {
        switch (transfer) {
            case LINEAR:
                return DoubleUnaryOperator.identity();
            case SRGB:
                return f -> {
                    if (f <= 0.00313066844250063D)
                        return f * 12.92D;
                    else
                        return 1.055D * Math.pow(f, 0.4166666666666667D) - 0.055D;
                };
            case PQ:
                return f -> {
                    double d = MathHelper.signedPow(f, 0.159423828125D);
                    return MathHelper.signedPow((0.8359375D + 18.8515625D * d) / (1D + 18.6875D * d), 78.84375D);
                };
            case BT709:
                return f -> {
                    if (f <= 0.018053968510807807336D)
                        return 4.5D * f;
                    else
                        return 1.0992968268094429403D * Math.pow(f, 0.45D) - 0.0992968268094429403D;
                };
            case DCI:
            case HLG:
                throw new UnsupportedOperationException("Not yet implemented");
        }

        if (transfer < (1 << 24)) {
            double gamma = 1e7D / transfer;
            return f -> Math.pow(f, gamma);
        }

        throw new IllegalArgumentException("Invalid transfer function");
    }
}
