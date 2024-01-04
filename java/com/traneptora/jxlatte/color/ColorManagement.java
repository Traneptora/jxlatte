package com.traneptora.jxlatte.color;

import java.util.function.DoubleUnaryOperator;

import com.traneptora.jxlatte.util.IntPoint;
import com.traneptora.jxlatte.util.MathHelper;

public final class ColorManagement {
    private ColorManagement() {

    }

    private static final float[][] BRADFORD = new float[][]{
        {0.8951f, 0.2664f, -0.1614f},
        {-0.7502f, 1.7135f, 0.0367f},
        {0.0389f, -0.0685f, 1.0296f}
    };

    private static final float[][] BRADFORD_INVERSE = MathHelper.invertMatrix3x3(BRADFORD);

    public static final CIEPrimaries PRI_SRGB = ColorFlags.getPrimaries(ColorFlags.PRI_SRGB);
    public static final CIEPrimaries PRI_BT2100 = ColorFlags.getPrimaries(ColorFlags.PRI_BT2100);
    public static final CIEPrimaries PRI_P3 = ColorFlags.getPrimaries(ColorFlags.PRI_P3);

    public static final CIEXY WP_D65 = ColorFlags.getWhitePoint(ColorFlags.WP_D65);
    public static final CIEXY WP_D50 = ColorFlags.getWhitePoint(ColorFlags.WP_D50);
    public static final CIEXY WP_DCI = ColorFlags.getWhitePoint(ColorFlags.WP_DCI);
    public static final CIEXY WP_E = ColorFlags.getWhitePoint(ColorFlags.WP_E);

    private static float[] getXYZ(CIEXY xy) {
        validateXY(xy);
        float invY = 1.0f / xy.y;
        return new float[]{xy.x * invY, 1.0f, (1.0f - xy.x - xy.y) * invY};
    }

    private static void validateXY(CIEXY xy) {
        if (xy.x < 0 || xy.x > 1 || xy.y <= 0 || xy.y > 1)
            throw new IllegalArgumentException();
    }

    private static void validateLMS(float[] lms) {
        for (int i = 0; i < lms.length; i++) {
            if (Math.abs(lms[i]) < 1e-8D) {
                throw new IllegalArgumentException();
            }
        }
    }

    private static float[][] adaptWhitePoint(CIEXY targetWP, CIEXY currentWP) {
        if (targetWP == null)
            targetWP = WP_D50;
        if (currentWP == null)
            currentWP = WP_D50;
        float[] wCurrent = getXYZ(currentWP);
        float[] lmsCurrent = MathHelper.matrixMutliply(BRADFORD, wCurrent);
        float[] wTarget = getXYZ(targetWP);
        float[] lmsTarget = MathHelper.matrixMutliply(BRADFORD, wTarget);
        validateLMS(lmsCurrent);
        float[][] a = new float[3][3];
        for (int i = 0; i < 3; i++)
            a[i][i] = lmsTarget[i] / lmsCurrent[i];
        return MathHelper.matrixMutliply(BRADFORD_INVERSE, a, BRADFORD);
    }

    private static float[][] primariesToXYZ(CIEPrimaries primaries, CIEXY wp) {
        if (primaries == null)
            return null;
        if (wp == null)
            wp = WP_D50;
        if (wp.x < 0 || wp.x > 1 || wp.y <= 0 || wp.y > 1)
            throw new IllegalArgumentException();
        float[][] primariesTr = new float[][]{
            getXYZ(primaries.red),
            getXYZ(primaries.green),
            getXYZ(primaries.blue)
        };
        float[][] primariesMatrix = MathHelper.transposeMatrix(primariesTr, new IntPoint(3));
        float[][] inversePrimaries = MathHelper.invertMatrix3x3(primariesMatrix);
        float[] w = getXYZ(wp);
        float[] xyz = MathHelper.matrixMutliply(inversePrimaries, w);
        float[][] a = new float[][]{{xyz[0], 0, 0}, {0, xyz[1], 0}, {0, 0, xyz[2]}};
        return MathHelper.matrixMutliply(primariesMatrix, a);
    }

    public static float[][] primariesToXYZD50(CIEPrimaries primaries, CIEXY wp) {
        float[][] whitePointConv = adaptWhitePoint(null, wp);
        return MathHelper.matrixMutliply(whitePointConv, primariesToXYZ(primaries, wp));
    }

    public static float[][] getConversionMatrix(CIEPrimaries targetPrim, CIEXY targetWP,
            CIEPrimaries currentPrim, CIEXY currentWP) {
        if (CIEPrimaries.matches(targetPrim, currentPrim) && CIEXY.matches(targetWP, currentWP))
            return MathHelper.matrixIdentity(3);
        float[][] whitePointConv = null;
        if (!CIEXY.matches(targetWP, currentWP))
            whitePointConv = adaptWhitePoint(targetWP, currentWP);
        float[][] forward = primariesToXYZ(currentPrim, currentWP);
        float[][] reverse = MathHelper.invertMatrix3x3(primariesToXYZ(targetPrim, targetWP));
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
                    double d = Math.pow(f, 0.159423828125D);
                    return Math.pow((0.8359375D + 18.8515625D * d) / (1D + 18.6875D * d), 78.84375D);
                };
            case ColorFlags.TF_BT709:
                return f -> {
                    if (f <= 0.018053968510807807336D)
                        return 4.5D * f;
                    else
                        return 1.0992968268094429403D * Math.pow(f, 0.45D) - 0.0992968268094429403D;
                };
            case ColorFlags.TF_DCI:
                transfer = 3846154;
                break;
            case ColorFlags.TF_HLG:
                throw new UnsupportedOperationException("Not yet implemented");
        }

        if (transfer < (1 << 24)) {
            double gamma = transfer * 1e-7D;
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
                    double d = Math.pow(f, 0.012683313515655965121D);
                    return Math.pow((d - 0.8359375D) / (18.8515625D + 18.6875D * d), 6.2725880551301684533D);
                };
            case ColorFlags.TF_DCI:
                transfer = 3846154;
                break;
            case ColorFlags.TF_HLG:
                throw new UnsupportedOperationException("Not yet implemented");
        }
    
        if (transfer < (1 << 24)) {
            double gamma = 1e7D / transfer;
            return f -> Math.pow(f, gamma);
        }
    
        throw new IllegalArgumentException("Invalid transfer function");
    }
}

