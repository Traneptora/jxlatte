package com.thebombzen.jxlatte.util;

import java.util.Arrays;
import java.util.stream.Stream;

public final class MathHelper {

    public static final double SQRT_2 = StrictMath.sqrt(2.0D);
    public static final double SQRT_H = StrictMath.sqrt(0.5D);
    public static final double SQRT_F = StrictMath.sqrt(0.125D);

    // s, n, k
    private static double[][][] cosineLut = new double[9][][];

    static {
        for (int l = 0; l < cosineLut.length; l++) {
            int s = 1 << l;
            cosineLut[l] = new double[s - 1][s];
            for (int n = 0; n < cosineLut[l].length; n++) {
                for (int k = 0; k < cosineLut[l][n].length; k++) {
                    cosineLut[l][n][k] = SQRT_2 * StrictMath.cos(Math.PI * (n + 1) * (k + 0.5D) / s);
                }
            }
        }
    }

    public static int unpackSigned(int value) {
        // prevent overflow and extra casework
        long v = (long)value & 0xFF_FF_FF_FFL;
        return (int)((v & 1L) == 0L ? v / 2L : -(v + 1L) / 2L);
    }

    public static int round(double d) {
        return (int)(d + 0.5D);
    }

    public static double erf(double z) {
        double az = Math.abs(z);
        double absErf;
        // first method is more accurate for most z, but becomes inaccurate for very small z
        // so we fall back on the second method
        if (az > 1e-4D) {
            /*
             * William H. Press, Saul A. Teukolsky, William T. Vetterling, and Brian P. Flannery. 1992.
             * Numerical recipes in C (2nd ed.): the art of scientific computing. Cambridge University Press, USA.
             */
            double t = 1.0D / (1.0D + 0.5D * az);
            double u = -1.26551223D + t * (1.00002368D + t * (0.37409196D + t * (0.09678418D + t * (-0.18628806D
                + t * (0.27886807D + t * (-1.13520398D + t * (1.48851587D + t * (-0.82215223D + t * 0.17087277D))))))));
            absErf = 1.0D - t * Math.exp(-z * z + u);
        } else {
            /*
             * Milton Abramowitz and Irene A. Stegun. 1964. Handbook of Mathematical Functions with formulas,
             * graphs, and mathematical tables, Dover Publications, USA.
             */
            double t = 1.0D / (1.0D + 0.47047D * az);
            double u = t * (0.3480242D + t * (-0.0958798D + t * 0.7478556D));
            absErf = 1.0D - u * Math.exp(-z * z);
        }
        if (z < 0)
            return -absErf;
        return absErf;
    }

    public static void inverseDCTHorizontal(double[][] src, int yIn, int xStartIn,
            double[][] dest, int yOut, int xStartOut, int xLogLength, int xLength) {
        for (int x = 0; x < xLength; x++) {
            dest[yOut][xStartOut + x] = src[yIn][xStartIn];
        }
        for (int k = 0; k < xLength; k++) {
            for (int n = 1; n < xLength; n++) {
                dest[yOut][xStartOut + k] += src[yIn][xStartIn + n] * cosineLut[xLogLength][n - 1][k];
            }
        }
    }

    public static void forwardDCTHorizontal(double[][] src, int yIn, int xStartIn,
            double[][] dest, int yOut, int xStartOut, int xLogLength, int xLength) {
        double invLength = 1D / xLength;
        for (int x = 0; x < xLength; x++) {
            dest[yOut][xStartOut] += src[yIn][xStartIn + x];
        }
        dest[yOut][xStartOut] *= invLength;
        for (int k = 1; k < xLength; k++) {
            for (int n = 0; n < xLength; n++) {
                dest[yOut][xStartOut + k] += src[yIn][xStartIn + n] * cosineLut[xLogLength][k - 1][n];
            }
            dest[yOut][xStartOut + k] *= invLength;
        }
    }

    public static void inverseDCTVertical(double[][] src, int xIn, int yStartIn,
            double[][] dest, int xOut, int yStartOut, int yLogLength, int yLength) {
        for (int y = 0; y < yLength; y++) {
            dest[yStartOut + y][xOut] = src[yStartIn][xIn];
        }
        for (int k = 0; k < yLength; k++) {
            for (int n = 1; n < yLength; n++) {
                dest[yStartOut + k][xOut] += src[yStartIn + n][xIn] * cosineLut[yLogLength][n - 1][k];
            }
        }
    }

    public static void forwardDCTVertical(double[][] src, int xIn, int yStartIn,
        double[][] dest, int xOut, int yStartOut, int yLogLength, int yLength) {
        double invLength = 1D / yLength;
        for (int y = 0; y < yLength; y++) {
            dest[yStartOut][xOut] += src[yStartIn + y][xIn];
        }
        dest[yStartOut][xOut] *= invLength;
        for (int k = 1; k < yLength; k++) {
            for (int n = 0; n < yLength; n++) {
                dest[yStartOut + k][xOut] += src[yStartIn + n][xIn] * cosineLut[yLogLength][k - 1][n];
            }
            dest[yStartOut + k][xOut] *= invLength;
        }
    }

    public static void inverseDCT2D(double[][] src, double[][] dest, IntPoint startIn, IntPoint startOut, IntPoint length, double[][] scratchSpace) {
        int xLogLength = ceilLog2(length.x);
        int yLogLength = ceilLog2(length.y);
        for (int x = 0; x < length.x; x++) {
            inverseDCTVertical(src, startIn.x + x, startIn.y, scratchSpace, x, 0, yLogLength, length.y);
        }
        for (int y = 0; y < length.y; y++) {
            inverseDCTHorizontal(scratchSpace, y, 0, dest, startOut.y + y, startOut.x, xLogLength, length.x);
        }
    }

    public static void forwardDCT2D(double[][] src, double[][] dest, IntPoint startIn, IntPoint startOut, IntPoint length) {
        int xLogLength = ceilLog2(length.x);
        int yLogLength = ceilLog2(length.y);
        double[][] temp = new double[length.y][length.x];
        for (int x = 0; x < length.x; x++) {
            forwardDCTVertical(src, startIn.x + x, startIn.y, temp, x, 0, yLogLength, length.y);
        }
        for (int y = 0; y < length.y; y++) {
            forwardDCTHorizontal(temp, y, 0, dest, startOut.y + y, startOut.x, xLogLength, length.x);
        }
    }

    public static void transposeMatrixInto(double[][] src, double[][] dest, IntPoint inSize) {
        for (IntPoint p : FlowHelper.range2D(inSize)) {
            dest[p.x][p.y] = src[p.y][p.x];
        }
    }

    public static double[][] transposeMatrix(double[][] matrix, IntPoint inSize) {
        double[][] dest = new double[inSize.x][inSize.y];
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

    public static double min(double... a) {
        return Arrays.stream(a).reduce(Double.MAX_VALUE, Math::min);
    }

    public static double max(double... a) {
        return Arrays.stream(a).reduce(Double.MIN_VALUE, Math::max);
    }

    public static double signedPow(double base, double exponent) {
        return Math.signum(base) * Math.pow(Math.abs(base), exponent);
    }

    public static int clamp(int v, int... a) {
        int lower = min(a);
        int upper = max(a);
        return Math.min(Math.max(v, lower), upper);
    }

    public static double clamp(double v, double... a) {
        double lower = min(a);
        double upper = max(a);
        return Math.min(Math.max(v, lower), upper);
    }

    public static double[] matrixMutliply(double[][] matrix, double[] vector) {
        if (matrix == null)
            return vector;
        if (matrix.length != vector.length || vector.length == 0)
            throw new IllegalArgumentException();
        double[] total = new double[matrix[0].length];
        for (int j = 0; j < total.length; j++) {
            for (int i = 0; i < vector.length; i++) {
                total[j] += matrix[i][j] * vector[i];
            }
        }
        return total;
    }

    public static double[][] matrixMutliply(double[][] left, double[][] right) {
        if (left == null)
            return right;
        if (right == null)
            return left;
        if (left.length == 0 || left[0].length == 0 || right.length == 0 || left.length != right[0].length)
            throw new IllegalArgumentException();
        double[][] result = new double[right.length][];
        for (int x = 0; x < right.length; x++)
            result[x] = matrixMutliply(left, right[x]);
        return result;
    }

    public static double[][] matrixIdentity(int n) {
        double[][] identity = new double[n][n];
        for (int i = 0; i < n; i++)
            identity[i][i] = 1.0D;
        return identity;
    }

    public static double[][] matrixMutliply(double[][]... matrices) {
        return Stream.of(matrices).reduce(MathHelper::matrixMutliply).orElse(null);
    }

    // expensive! try not to use on the fly
    public static double[][] invertMatrix3x3(double[][] matrix) {
        if (matrix == null)
            return null;
        if (matrix.length != 3)
            throw new IllegalArgumentException();
        double det = 0D;
        for (int c = 0; c < 3; c++) {
            if (matrix[c].length != 3)
                throw new IllegalArgumentException();
            int c1 = (c + 1) % 3;
            int c2 = (c + 2) % 3;
            det += matrix[c][0] * matrix[c1][1] * matrix[c2][2] - matrix[c][0] * matrix[c1][2] * matrix[c2][1];
        }
        if (det == 0D)
            return null;
        double invDet = 1D / det;
        double[][] inverse = new double[3][3];
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
}
