package com.thebombzen.jxlatte.util;

import java.util.Arrays;
import java.util.stream.Stream;

public final class MathHelper {

    public static final float SQRT_2 = (float)StrictMath.sqrt(2.0D);
    public static final float SQRT_H = (float)StrictMath.sqrt(0.5D);
    public static final float SQRT_F = (float)StrictMath.sqrt(0.125D);
    public static final float PHI_BAR = (float)((StrictMath.sqrt(5D) * 0.5D) - 0.5D);
    public static final float PHI = (float)((StrictMath.sqrt(5D) * 0.5D) + 0.5D);

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
        return (value & 1) == 0 ? (value >>> 1) : (~value >>> 1) | 0x80_00_00_00;
    }

    public static int round(float d) {
        return (int)(d + 0.5f);
    }

    public static float erf(float z) {
        final float az = Math.abs(z);
        float absErf;
        // first method is more accurate for most z, but becomes inaccurate for very small z
        // so we fall back on the second method
        if (az > 1e-4f) {
            /*
             * William H. Press, Saul A. Teukolsky, William T. Vetterling, and Brian P. Flannery. 1992.
             * Numerical recipes in C (2nd ed.): the art of scientific computing. Cambridge University Press, USA.
             */
            final float t = 1.0f / (az * 0.5f + 1.0f);
            final float u = t * (t * (t * (t * (t * (t * (t * (t * (t * 0.17087277f - 0.82215223f) + 1.48851587f) - 1.13520398f)
                              + 0.27886807f) - 0.18628806f) + 0.09678418f) + 0.37409196f) + 1.00002368f) - 1.26551223f;
            absErf = 1.0f - t * (float)Math.exp(-z * z + u);
        } else {
            /*
             * Milton Abramowitz and Irene A. Stegun. 1964. Handbook of Mathematical Functions with formulas,
             * graphs, and mathematical tables, fover Publications, USA.
             */
            final float t = 1.0f / (az * 0.47047f + 1.0f);
            final float u = t * (t * (t * 0.7478556f - 0.0958798f) + 0.3480242f);
            absErf = 1.0f - u * (float)Math.exp(-z * z);
        }
        if (z < 0)
            return -absErf;
        return absErf;
    }

    public static void inverseDCTHorizontal(final float[][] src, final int yIn, final int xStartIn,
            final float[][] dest, final int yOut, final int xStartOut, final int xLogLength, final int xLength) {
        final float[] d = dest[yOut];
        final float[] s = src[yIn];
        Arrays.fill(d, xStartOut, xStartOut + xLength, s[xStartIn]);
        for (int n = 1; n < xLength; n++) {
            final float[] lut = cosineLut[xLogLength][n - 1];
            final float s2 = s[xStartIn + n];
            for (int k = 0; k < xLength; k++)
                d[xStartOut + k] += s2 * lut[k];
        }
    }

    public static void forwardDCTHorizontal(final float[][] src, final int yIn, final int xStartIn,
            final float[][] dest, final int yOut, final int xStartOut, final int xLogLength, final int xLength) {
        final float invLength = 1f / xLength;
        final float[] d = dest[yOut];
        final float[] s = src[yIn];
        float d2 = 0f;
        for (int x = 0; x < xLength; ++x)
            d2 += s[xStartIn + x];
        d[xStartOut] = d2 * invLength;
        for (int k = 1; k < xLength; ++k) {
            d2 = 0f;
            final float[] lut = cosineLut[xLogLength][k - 1];
            for (int n = 0; n < xLength; ++n)
                d2 += s[xStartIn + n] * lut[n];
            d[xStartOut + k] = d2 * invLength;
        }
    }

    public static void inverseDCT2D(final float[][] src, final float[][] dest, final IntPoint startIn,
            final IntPoint startOut, final IntPoint length, final float[][] scratchSpace1,
            final float[][] scratchSpace2, boolean transposed) {
        final int xLogLength = ceilLog2(length.x);
        final int yLogLength = ceilLog2(length.y);
        for (int y = 0; y < length.y; y++)
            inverseDCTHorizontal(src, y + startIn.y, startIn.x, scratchSpace1, y, 0, xLogLength, length.x);
        transposeMatrixInto(scratchSpace1, scratchSpace2, IntPoint.ZERO, IntPoint.ZERO, length);
        if (transposed) {
            for (int y = 0; y < length.x; y++)
                inverseDCTHorizontal(scratchSpace2, y, 0, dest,
                    startOut.y + y, startOut.x, yLogLength, length.y);
        } else {
            for (int x = 0; x < length.x; x++)
                inverseDCTHorizontal(scratchSpace2, x, 0, scratchSpace1,
                    x, 0, yLogLength, length.y);
            transposeMatrixInto(scratchSpace1, dest, IntPoint.ZERO, startOut, length.transpose());  
        }
    }

    public static void forwardDCT2D(final float[][] src, final float[][] dest, final IntPoint startIn, final IntPoint startOut, final IntPoint length, final float[][] scratchSpace1, final float[][] scratchSpace2) {
        final int xLogLength = ceilLog2(length.x);
        final int yLogLength = ceilLog2(length.y);
        for (int y = 0; y < length.y; y++)
            forwardDCTHorizontal(src, y + startIn.y, startIn.x, scratchSpace1, y, 0, xLogLength, length.x);
        transposeMatrixInto(scratchSpace1, scratchSpace2, IntPoint.ZERO, IntPoint.ZERO, length);
        for (int x = 0; x < length.x; x++)
            forwardDCTHorizontal(scratchSpace2, x, 0, scratchSpace1, x, 0, yLogLength, length.y);
        transposeMatrixInto(scratchSpace1, dest, IntPoint.ZERO, startOut, length.transpose());
    }

    public static void transposeMatrixInto(final float[][] src, final float[][] dest, IntPoint srcStart, IntPoint destStart, IntPoint srcSize) {
        for (int y = 0; y < srcSize.y; y++) {
            final float[] srcy = src[srcStart.y + y];
            for (int x = 0; x < srcSize.x; x++)
                dest[destStart.y + x][destStart.x + y] = srcy[srcStart.x + x];
        }
    }

    public static float[][] transposeMatrix(float[][] matrix, IntPoint inSize) {
        final float[][] dest = new float[inSize.x][inSize.y];
        transposeMatrixInto(matrix, dest, IntPoint.ZERO, IntPoint.ZERO, inSize);
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
            if (f > result)
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

    public static float[] matrixMutliply(final float[][] matrix, final float[] columnVector) {
        if (matrix == null)
            return columnVector;
        if (matrix[0].length > columnVector.length || columnVector.length == 0)
            throw new IllegalArgumentException();
        int extra = columnVector.length - matrix[0].length;
        float[] total = new float[matrix.length + extra];
        for (int y = 0; y < matrix.length; y++) {
            final float[] row = matrix[y];
            for (int x = 0; x < row.length; x++)
                total[y] += row[x] * columnVector[x];
        }
        if (extra != 0)
            System.arraycopy(columnVector, columnVector.length - extra, total, total.length - extra, extra);
        return total;
    }

    public static float[] matrixMutliply(final float[] rowVector, final float[][] matrix) {
        if (matrix == null)
            return rowVector;
        if (matrix.length != rowVector.length || rowVector.length == 0)
            throw new IllegalArgumentException();
        float[] total = new float[matrix[0].length];
        for (int y = 0; y < rowVector.length; y++) {
            final float[] row = matrix[y];
            for (int x = 0; x < total.length; x++)
                total[x] += rowVector[y] * row[x];
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
