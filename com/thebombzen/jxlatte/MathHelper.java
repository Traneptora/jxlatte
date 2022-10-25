package com.thebombzen.jxlatte;

public final class MathHelper {

    private MathHelper() {

    }

    public static int unpackSigned(int value) {
        // prevent overflow and extra casework
        long v = (long)value & 0xFF_FF_FF_FFL;
        return (int)((v & 1L) == 0 ? v / 2L : -(v + 1L) / 2L);
    }

    /**
     * @return ceil(log2(x + 1))
     */
    public static int ceilLog1p(long x) {
        return 64 - Long.numberOfLeadingZeros(x);
    }

    /**
     * @return ceil(log2(x + 1))
     */
    public static int ceilLog1p(int x) {
        return 32 - Integer.numberOfLeadingZeros(x);
    }

    public static int min3(int a, int b, int c) {
        return Math.min(a, Math.min(b, c));
    }

    public static int max3(int a, int b, int c) {
        return Math.max(a, Math.max(b, c));
    }

    public static int clamp(int v, int min, int max) {
        int lower = Math.min(min, max);
        int upper = Math.max(min, max);
        return Math.min(Math.max(v, lower), upper);
    }
}
