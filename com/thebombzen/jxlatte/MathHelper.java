package com.thebombzen.jxlatte;

public final class MathHelper {

    private MathHelper() {

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

}
