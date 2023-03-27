package com.thebombzen.jxlatte.entropy;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.MathHelper;

public class HybridIntegerConfig {
    public final int splitExponent;
    public final int msbInToken;
    public final int lsbInToken;

    public HybridIntegerConfig(int splitExponent, int msbInToken, int lsbInToken) {
        this.splitExponent = splitExponent;
        this.msbInToken = msbInToken;
        this.lsbInToken = lsbInToken;
    }

    public HybridIntegerConfig(Bitreader reader, int logAlphabetSize) throws IOException {
        splitExponent = reader.readBits(MathHelper.ceilLog1p(logAlphabetSize));
        if (splitExponent == logAlphabetSize) {
            msbInToken = lsbInToken = 0;
            return;
        }
        msbInToken = reader.readBits(MathHelper.ceilLog1p(splitExponent));
        if (msbInToken > splitExponent)
            throw new InvalidBitstreamException("msbInToken is too large");
        lsbInToken = reader.readBits(MathHelper.ceilLog1p(splitExponent - msbInToken));
        if (msbInToken + lsbInToken > splitExponent)
            throw new InvalidBitstreamException("msbInToken + lsbInToken is too large");
    }

    @Override
    public String toString() {
        return this.splitExponent + "-" + this.msbInToken + "-" + this.lsbInToken;
    }
}
