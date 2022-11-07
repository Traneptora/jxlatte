package com.thebombzen.jxlatte.util;

import java.util.function.DoubleUnaryOperator;

import com.thebombzen.jxlatte.bundle.color.CIEPrimaries;
import com.thebombzen.jxlatte.bundle.color.CIEXY;
import com.thebombzen.jxlatte.bundle.color.ColorFlags;

public final class ColorManagement {
    private ColorManagement() {

    }

    private static final double[][] BRADFORD = new double[][]{
        {0.8951D, -0.7502D, 0.0389D},
        {0.2664D, 1.7135D, -0.0685D},
        {-0.1614D, 0.0367D, 1.0296D}
    };

    private static final double[][] BRADFORD_INVERSE = MathHelper.invertMatrix3x3(BRADFORD);

    private static double[] getXYZ(CIEXY xy) {
        validateXY(xy);
        double invY = 1.0D / xy.y;
        return new double[]{xy.x * invY, 1.0D, (1.0D - xy.x - xy.y) * invY};
    }

    private static void validateXY(CIEXY xy) {
        if (xy.x < 0 || xy.x > 1 || xy.y <= 0 || xy.y > 1)
            throw new IllegalArgumentException();
    }

    private static void validateLMS(double[] lms) {
        for (int i = 0; i < lms.length; i++) {
            if (Math.abs(lms[i]) < 1e-8D) {
                throw new IllegalArgumentException();
            }
        }
    }

    private static double[][] adaptWhitePoint(CIEXY targetWP, CIEXY currentWP) {
        double[] wCurrent = getXYZ(currentWP);
        double[] lmsCurrent = MathHelper.matrixMutliply(BRADFORD, wCurrent);
        double[] wTarget = getXYZ(targetWP);
        double[] lmsTarget = MathHelper.matrixMutliply(BRADFORD, wTarget);
        validateLMS(lmsCurrent);
        double[][] a = new double[3][3];
        for (int i = 0; i < 3; i++)
            a[i][i] = lmsTarget[i] / lmsCurrent[i];
        return MathHelper.matrixMutliply(BRADFORD_INVERSE, a, BRADFORD);
    }

    private static double[][] primariesToXYZ(CIEPrimaries primaries, CIEXY wp) {
        if (wp.x < 0 || wp.x > 1 || wp.y <= 0 || wp.y > 1)
            throw new IllegalArgumentException();
        double[][] primaryMatrix = new double[][]{getXYZ(primaries.red), getXYZ(primaries.green), getXYZ(primaries.blue)};
        double[][] inversePrimaries = MathHelper.invertMatrix3x3(primaryMatrix);
        double[] w = getXYZ(wp);
        double[] xyz = MathHelper.matrixMutliply(inversePrimaries, w);
        double[][] a = new double[][]{{xyz[0], 0, 0}, {0, xyz[1], 0}, {0, 0, xyz[2]}};
        return MathHelper.matrixMutliply(primaryMatrix, a);
    }

    public static double[][] getConversionMatrix(CIEPrimaries targetPrim, CIEXY targetWP,
            CIEPrimaries currentPrim, CIEXY currentWP) {
        if (targetPrim.matches(currentPrim) && targetWP.matches(currentWP))
            return MathHelper.matrixIdentity(3);
        double[][] whitePointConv = null;
        if (!targetWP.matches(currentWP))
            whitePointConv = adaptWhitePoint(targetWP, currentWP);
        double[][] forward = primariesToXYZ(currentPrim, currentWP);
        double[][] reverse = MathHelper.invertMatrix3x3(primariesToXYZ(targetPrim, targetWP));
        return MathHelper.matrixMutliply(reverse, whitePointConv, forward);
    }

    public static DoubleUnaryOperator getTransferFunction(int transfer) {
        switch (transfer) {
            case ColorFlags.TF_LINEAR:
                return DoubleUnaryOperator.identity();
            case ColorFlags.TF_SRGB:
                return f -> {
                    if (f <= 0.00313066844250063D)
                        return f * 12.92D;
                    else
                        return 1.055D * Math.pow(f, 0.4166666666666667D) - 0.055D;
                };
            case ColorFlags.TF_PQ:
                return f -> {
                    double d = MathHelper.signedPow(f, 0.159423828125D);
                    return MathHelper.signedPow((0.8359375D + 18.8515625D * d) / (1D + 18.6875D * d), 78.84375D);
                };
            case ColorFlags.TF_BT709:
                return f -> {
                    if (f <= 0.018053968510807807336D)
                        return 4.5D * f;
                    else
                        return 1.0992968268094429403D * Math.pow(f, 0.45D) - 0.0992968268094429403D;
                };
            case ColorFlags.TF_DCI:
            case ColorFlags.TF_HLG:
                throw new UnsupportedOperationException("Not yet implemented");
        }

        if (transfer < (1 << 24)) {
            double gamma = 1e7D / transfer;
            return f -> Math.pow(f, gamma);
        }

        throw new IllegalArgumentException("Invalid transfer function");
    }

    public static DoubleUnaryOperator getInverseTransferFunction(int transfer) {
        switch (transfer) {
            case ColorFlags.TF_LINEAR:
                return DoubleUnaryOperator.identity();
            case ColorFlags.TF_SRGB:
                return f -> {
                    if (f <= 0.0404482362771082D)
                        return f * 0.07739938080495357D;
                    else
                        return Math.pow((f + 0.055D) * 0.9478672985781991D, 2.4D);
                };
            case ColorFlags.TF_BT709:
                return f -> {
                    if (f <= 0.081242858298635133011D)
                        return f * 0.22222222222222222222D;
                    else
                        return Math.pow((f + 0.0992968268094429403D) * 0.90967241568627260377D, 2.2222222222222222222D);
                };
            case ColorFlags.TF_PQ:
                return f -> {
                    double d = MathHelper.signedPow(f, 0.012683313515655965121D);
                    return MathHelper.signedPow((d - 0.8359375D) / (18.8515625D + 18.6875D * d), 6.2725880551301684533D);
                };
            case ColorFlags.TF_DCI:
            case ColorFlags.TF_HLG:
                throw new UnsupportedOperationException("Not yet implemented");
        }
    
        if (transfer < (1 << 24)) {
            double gamma = transfer * 1e-7D;
            return f -> MathHelper.signedPow(f, gamma);
        }
    
        throw new IllegalArgumentException("Invalid transfer function");
    }
}

