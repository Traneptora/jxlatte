package com.thebombzen.jxlatte.util;

import java.util.Arrays;
import java.util.stream.Stream;

public final class MathHelper {

    public static final float SQRT_2 = (float)StrictMath.sqrt(2.0D);
    public static final float SQRT_H = (float)StrictMath.sqrt(0.5D);
    public static final float SQRT_F = (float)StrictMath.sqrt(0.125D);

    // s, n, k
    private static float[][][] cosineLut = new float[9][][];

    static {
        for (int l = 0; l < cosineLut.length; l++) {
            int s = 1 << l;
            cosineLut[l] = new float[s - 1][s];
            for (int n = 0; n < cosineLut[l].length; n++) {
                for (int k = 0; k < cosineLut[l][n].length; k++) {
                    cosineLut[l][n][k] = (float)(SQRT_2 * StrictMath.cos(Math.PI * (n + 1) * (k + 0.5D) / s));
                }
            }
        }
    }

    public static int unpackSigned(int value) {
        // prevent overflow and extra casework
        long v = (long)value & 0xFF_FF_FF_FFL;
        return (int)((v & 1L) == 0L ? v / 2L : -(v + 1L) / 2L);
    }

    public static int round(float d) {
        return (int)(d + 0.5f);
    }

    public static float erf(float z) {
        float az = Math.abs(z);
        float absErf;
        // first method is more accurate for most z, but becomes inaccurate for very small z
        // so we fall back on the second method
        if (az > 1e-4f) {
            /*
             * William H. Press, Saul A. Teukolsky, William T. Vetterling, and Brian P. Flannery. 1992.
             * Numerical recipes in C (2nd ed.): the art of scientific computing. Cambridge University Press, USA.
             */
            float t = 1.0f / (1.0f + 0.5f * az);
            float u = -1.26551223f + t * (1.00002368f + t * (0.37409196f + t * (0.09678418f + t * (-0.18628806f
                + t * (0.27886807f + t * (-1.13520398f + t * (1.48851587f + t * (-0.82215223f + t * 0.17087277f))))))));
            absErf = 1.0f - t * (float)Math.exp(-z * z + u);
        } else {
            /*
             * Milton Abramowitz and Irene A. Stegun. 1964. Handbook of Mathematical Functions with formulas,
             * graphs, and mathematical tables, fover Publications, USA.
             */
            float t = 1.0f / (1.0f + 0.47047f * az);
            float u = t * (0.3480242f + t * (-0.0958798f + t * 0.7478556f));
            absErf = 1.0f - u * (float)Math.exp(-z * z);
        }
        if (z < 0)
            return -absErf;
        return absErf;
    }

    public static void inverseDCTHorizontal(float[][] src, int yIn, int xStartIn,
            float[][] dest, int yOut, int xStartOut, int xLogLength, int xLength) {
        for (int x = 0; x < xLength; x++) {
            dest[yOut][xStartOut + x] = src[yIn][xStartIn];
        }
        for (int k = 0; k < xLength; k++) {
            for (int n = 1; n < xLength; n++) {
                dest[yOut][xStartOut + k] += src[yIn][xStartIn + n] * cosineLut[xLogLength][n - 1][k];
            }
        }
    }

    public static void forwardDCTHorizontal(float[][] src, int yIn, int xStartIn,
            float[][] dest, int yOut, int xStartOut, int xLogLength, int xLength) {
        float invLength = 1f / xLength;
        dest[yOut][xStartOut] = 0;
        for (int x = 0; x < xLength; x++) {
            dest[yOut][xStartOut] += src[yIn][xStartIn + x];
        }
        dest[yOut][xStartOut] *= invLength;
        for (int k = 1; k < xLength; k++) {
            dest[yOut][xStartOut + k] = 0;
            for (int n = 0; n < xLength; n++) {
                dest[yOut][xStartOut + k] += src[yIn][xStartIn + n] * cosineLut[xLogLength][k - 1][n];
            }
            dest[yOut][xStartOut + k] *= invLength;
        }
    }

    public static void inverseDCTVertical(float[][] src, int xIn, int yStartIn,
            float[][] dest, int xOut, int yStartOut, int yLogLength, int yLength) {
        for (int y = 0; y < yLength; y++) {
            dest[yStartOut + y][xOut] = src[yStartIn][xIn];
        }
        for (int k = 0; k < yLength; k++) {
            for (int n = 1; n < yLength; n++) {
                dest[yStartOut + k][xOut] += src[yStartIn + n][xIn] * cosineLut[yLogLength][n - 1][k];
            }
        }
    }

    public static void forwardDCTVertical(float[][] src, int xIn, int yStartIn,
        float[][] dest, int xOut, int yStartOut, int yLogLength, int yLength) {
        float invLength = 1f / yLength;
        dest[yStartOut][xOut] = 0;
        for (int y = 0; y < yLength; y++) {
            dest[yStartOut][xOut] += src[yStartIn + y][xIn];
        }
        dest[yStartOut][xOut] *= invLength;
        for (int k = 1; k < yLength; k++) {
            dest[yStartOut + k][xOut] = 0;
            for (int n = 0; n < yLength; n++) {
                dest[yStartOut + k][xOut] += src[yStartIn + n][xIn] * cosineLut[yLogLength][k - 1][n];
            }
            dest[yStartOut + k][xOut] *= invLength;
        }
    }

    public static void inverseDCT2D(float[][] src, float[][] dest, IntPoint startIn, IntPoint startOut, IntPoint length, float[][] scratchSpace) {
        int xLogLength = ceilLog2(length.x);
        int yLogLength = ceilLog2(length.y);
        for (int x = 0; x < length.x; x++) {
            inverseDCTVertical(src, startIn.x + x, startIn.y, scratchSpace, x, 0, yLogLength, length.y);
        }
        for (int y = 0; y < length.y; y++) {
            inverseDCTHorizontal(scratchSpace, y, 0, dest, startOut.y + y, startOut.x, xLogLength, length.x);
        }
    }

    public static void forwardDCT2D(float[][] src, float[][] dest, IntPoint startIn, IntPoint startOut, IntPoint length) {
        int xLogLength = ceilLog2(length.x);
        int yLogLength = ceilLog2(length.y);
        float[][] temp = new float[length.y][length.x];
        for (int x = 0; x < length.x; x++) {
            forwardDCTVertical(src, startIn.x + x, startIn.y, temp, x, 0, yLogLength, length.y);
        }
        for (int y = 0; y < length.y; y++) {
            forwardDCTHorizontal(temp, y, 0, dest, startOut.y + y, startOut.x, xLogLength, length.x);
        }
    }

    public static void transposeMatrixInto(float[][] src, float[][] dest, IntPoint inSize) {
        for (IntPoint p : FlowHelper.range2D(inSize)) {
            dest[p.x][p.y] = src[p.y][p.x];
        }
    }

    public static float[][] transposeMatrix(float[][] matrix, IntPoint inSize) {
        float[][] dest = new float[inSize.x][inSize.y];
        transposeMatrixInto(matrix, dest, inSize);
        return dest;
    }

    /**
     * @return ceil(log2(x + 1))
     */
    public static int ceilLog1p(long x) {
        return 64 - Long.numberOfLeadingZeros(x);
    }

    public static int ceilLog2(long x) {
        return ceilLog1p(x - 1);
    }

    public static int ceilDiv(int numerator, int denominator) {
        return ((numerator - 1) / denominator) + 1;
    }

    public static int floorLog1p(long x) {
        int c = ceilLog1p(x);
        // if x + 1 is not a power of 2
        if (((x + 1) & x) != 0)
            return c - 1;
        return c;
    }

    public static int min(int... a) {
        return Arrays.stream(a).reduce(Integer.MAX_VALUE, Math::min);
    }

    public static int max(int... a) {
        return Arrays.stream(a).reduce(Integer.MIN_VALUE, Math::max);
    }

    public static float max(float... a) {
        float result = Float.MIN_VALUE;
        for (float f : a) {
            if (f < result)
                result = f;
        }
        return result;
    }

    public static float signedPow(float base, float exponent) {
        return Math.signum(base) * (float)Math.pow(Math.abs(base), exponent);
    }

    public static int clamp(int v, int a, int b, int c) {
        int lower = min(a, b, c);
        int upper = max(a, b, c);
        return Math.min(Math.max(v, lower), upper);
    }

    public static int clamp(int v, int a, int b) {
        int lower = Math.min(a, b);
        int upper = Math.max(a, b);
        return Math.min(Math.max(v, lower), upper);
    }

    public static float clamp(float v, float a, float b) {
        float lower = Math.min(a, b);
        float upper = Math.max(a, b);
        return Math.min(Math.max(v, lower), upper);
    }

    public static float[] matrixMutliply(float[][] matrix, float[] columnVector) {
        if (matrix == null)
            return columnVector;
        if (matrix[0].length != columnVector.length || columnVector.length == 0)
            throw new IllegalArgumentException();
        float[] total = new float[matrix.length];
        for (int y = 0; y < total.length; y++) {
            for (int x = 0; x < columnVector.length; x++) {
                total[y] += matrix[y][x] * columnVector[x];
            }
        }
        return total;
    }

    public static float[] matrixMutliply(float[] rowVector, float[][] matrix) {
        if (matrix == null)
            return rowVector;
        if (matrix.length != rowVector.length || rowVector.length == 0)
            throw new IllegalArgumentException();
        float[] total = new float[matrix[0].length];
        for (int x = 0; x < total.length; x++) {
            for (int y = 0; y < rowVector.length; y++) {
                total[x] += rowVector[y] * matrix[y][x];
            }
        }
        return total;
    }

    public static float[][] matrixMutliply(float[][] left, float[][] right) {
        if (left == null)
            return right;
        if (right == null)
            return left;
        if (left.length == 0 || left[0].length == 0 || right.length == 0 || left.length != right[0].length)
            throw new IllegalArgumentException();
        float[][] result = new float[left.length][right[0].length];
        for (int y = 0; y < right.length; y++)
            result[y] = matrixMutliply(left[y], right);
        return result;
    }

    public static float[][] matrixIdentity(int n) {
        float[][] identity = new float[n][n];
        for (int i = 0; i < n; i++)
            identity[i][i] = 1.0f;
        return identity;
    }

    public static float[][] matrixMutliply(float[][]... matrices) {
        return Stream.of(matrices).reduce(MathHelper::matrixMutliply).orElse(null);
    }

    // expensive! try not to use on the fly
    public static float[][] invertMatrix3x3(float[][] matrix) {
        if (matrix == null)
            return null;
        if (matrix.length != 3)
            throw new IllegalArgumentException();
        float det = 0f;
        for (int c = 0; c < 3; c++) {
            if (matrix[c].length != 3)
                throw new IllegalArgumentException();
            int c1 = (c + 1) % 3;
            int c2 = (c + 2) % 3;
            det += matrix[c][0] * matrix[c1][1] * matrix[c2][2] - matrix[c][0] * matrix[c1][2] * matrix[c2][1];
        }
        if (det == 0f)
            return null;
        float invDet = 1f / det;
        float[][] inverse = new float[3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                int x1 = (x + 1) % 3;
                int x2 = (x + 2) % 3;
                int y1 = (y + 1) % 3;
                int y2 = (y + 2) % 3;
                // because we're going cyclicly here we don't need to multiply by (-1)^(x + y)
                inverse[y][x] = (matrix[x1][y1] * matrix[x2][y2] - matrix[x2][y1] * matrix[x1][y2]) * invDet;
            }
        }
        return inverse;
    }

    private MathHelper() {}

    public static int mirrorCoordinate(int coordinate, int size) {
        if (coordinate < 0)
            return mirrorCoordinate(-coordinate - 1, size);
        if (coordinate >= size)
            return mirrorCoordinate(2 * size - coordinate - 1, size);
        return coordinate;
    }
}
