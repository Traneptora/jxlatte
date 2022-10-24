package com.thebombzen.jxlatte.bundle.color;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.io.Bitreader;

public class TransferFunction {
    public final boolean usesGamma;
    public final float gamma;
    public final EnumTransferFunction enumTransferFunction;
    public final FloatFunction transferFunction;

    public TransferFunction() {
        usesGamma = false;
        gamma = 0f;
        enumTransferFunction = EnumTransferFunction.SRGB;
        transferFunction = enumTransferFunction.transferFunction;
    }

    public TransferFunction(Bitreader reader) throws IOException {
        usesGamma = reader.readBool();
        if (usesGamma) {
            int g = reader.readBits(24);
            if (g == 0)
                throw new InvalidBitstreamException("Gamma may not be zero.");
            gamma = 1e7f / g;
            enumTransferFunction = EnumTransferFunction.UNKNOWN;
            transferFunction = f -> (float)Math.pow(f, gamma);
        } else {
            gamma = 0f;
            int tr = reader.readEnum();
            enumTransferFunction = EnumTransferFunction.getForIndex(tr);
            if (enumTransferFunction == null)
                throw new InvalidBitstreamException(String.format("Unrecognized EnumTransferFunction: %d", tr));
            transferFunction = enumTransferFunction.transferFunction;
        }
    }
}
