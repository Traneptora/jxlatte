package com.traneptora.jxlatte.frame.vardct;

import java.util.Arrays;

public class DCTParams {
    public final float[][] dctParam;
    public final float[][] param;
    public final int mode;
    public final float denominator;
    public final float[][] params4x4;

    public DCTParams(float[][] dctParams, float[][] params, int mode) {
        this(dctParams, params, null, mode, 1f);
    }

    public DCTParams(float[][] dctParams, float[][] params, float[][] params4x4, int mode) {
        this(dctParams, params, params4x4, mode, 1f);
    }

    public DCTParams(float[][] dctParams, float[][] params, int mode, float denominator) {
        this(dctParams, params, null, mode, denominator);
    }

    public DCTParams(float[][] dctParam, float[][] param, float[][] params4x4, int mode, float denominator) {
        this.dctParam = dctParam;
        this.param = param;
        this.mode = mode;
        this.denominator = denominator;
        this.params4x4 = params4x4;
    }

    public String toString() {
        return String.format("[%s, %s, %d, %f, %s]",
            Arrays.deepToString(dctParam), Arrays.deepToString(param),
            mode, denominator, Arrays.deepToString(params4x4));
    }

}
