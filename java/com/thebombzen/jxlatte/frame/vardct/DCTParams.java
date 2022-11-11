package com.thebombzen.jxlatte.frame.vardct;

public class DCTParams {
    public final double[][] dctParam;
    public final double[][] param;
    public final int mode;
    public final double denominator;
    public final double[][] params4x4;

    public DCTParams(double[][] dctParams, double[][] params, int mode) {
        this(dctParams, params, null, mode, 1D);
    }

    public DCTParams(double[][] dctParams, double[][] params, double[][] params4x4, int mode) {
        this(dctParams, params, params4x4, mode, 1D);
    }

    public DCTParams(double[][] dctParams, double[][] params, int mode, double denominator) {
        this(dctParams, params, null, mode, denominator);
    }

    public DCTParams(double[][] dctParam, double[][] param, double[][] params4x4, int mode, double denominator) {
        this.dctParam = dctParam;
        this.param = param;
        this.mode = mode;
        this.denominator = denominator;
        this.params4x4 = params4x4;
    }
}
