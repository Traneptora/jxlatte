package com.traneptora.jxlatte.color;

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

    public static CIEXY getWhitePoint(int wp) {
        switch(wp) {
            case ColorFlags.WP_D65:
                return WP_D65;
            case ColorFlags.WP_D50:
                return WP_D50;
            case ColorFlags.WP_DCI:
                return WP_DCI;
            case ColorFlags.WP_E:
                return WP_E;
            default:
                return null;
        }
    }

    public static CIEPrimaries getPrimaries(int prim) {
        switch (prim) {
            case ColorFlags.PRI_SRGB:
                return PRI_SRGB;
            case ColorFlags.PRI_P3:
                return PRI_P3;
            case ColorFlags.PRI_BT2100:
                return PRI_BT2100;
            default:
                return null;
        }
    }

    public static int getWhitePoint(CIEXY xy) {
        int[] wp = new int[]{ColorFlags.WP_D65, ColorFlags.WP_E, ColorFlags.WP_DCI, ColorFlags.WP_D50};
        for (int white : wp) {
            if (CIEXY.matches(xy, getWhitePoint(white)))
                return white;
        }
        return ColorFlags.WP_CUSTOM;
    }

    public static int getPrimaries(CIEPrimaries primaries) {
        int[] pri = new int[]{ColorFlags.PRI_SRGB, ColorFlags.PRI_BT2100, ColorFlags.PRI_P3};
        for (int prim : pri) {
            if (CIEPrimaries.matches(primaries, getPrimaries(prim)))
                return prim;
        }
        return ColorFlags.PRI_CUSTOM;
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
            getXYZ(primaries.blue),
        };
        float[][] primariesMatrix = MathHelper.transposeMatrix(primariesTr, 3, 3);
        float[][] inversePrimaries = MathHelper.invertMatrix3x3(primariesMatrix);
        float[] w = getXYZ(wp);
        float[] xyz = MathHelper.matrixMutliply(inversePrimaries, w);
        float[][] a = new float[][]{
            {xyz[0], 0, 0},
            {0, xyz[1], 0},
            {0, 0, xyz[2]},
        };
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

    public static TransferFunction getTransferFunction(int transfer) {
        switch (transfer) {
            case ColorFlags.TF_LINEAR:
                return TransferFunction.TF_LINEAR;
            case ColorFlags.TF_SRGB:
                return TransferFunction.TF_SRGB;
            case ColorFlags.TF_PQ:
                return TransferFunction.TF_PQ;
            case ColorFlags.TF_BT709:
                return TransferFunction.TF_BT709;
            case ColorFlags.TF_DCI:
                return TransferFunction.TF_DCI;
            case ColorFlags.TF_HLG:
                throw new UnsupportedOperationException("Not yet implemented");
        }

        if (transfer < (1 << 24)) {
            return new GammaTransferFunction(transfer);
        }

        throw new IllegalArgumentException("Invalid transfer function");
    }
}
