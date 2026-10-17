package com.traneptora.jxlatte.color;

public class GammaTransferFunction implements TransferFunction {

    private final double gamma;
    private final double inverseGamma;

    public GammaTransferFunction(int transfer) {
        this.gamma = 1e-7D * transfer;
        this.inverseGamma = 1e7D / transfer;
    }

    public double fromLinear(double f) {
        return Math.pow(f, gamma);
    }

    public double toLinear(double f) {
        return Math.pow(f, inverseGamma);
    }
}
