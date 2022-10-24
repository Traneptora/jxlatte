package com.thebombzen.jxlatte.entropy;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.MathHelper;
import com.thebombzen.jxlatte.io.Bitreader;

public class HybridUintConfig {
    public int splitExponent;
    public int msbInToken;
    public int lsbInToken;

    public HybridUintConfig() {

    }

    public HybridUintConfig(int splitExponent, int msbInToken, int lsbInToken) {
        this.splitExponent = splitExponent;
        this.msbInToken = msbInToken;
        this.lsbInToken = lsbInToken;
    }

    public HybridUintConfig(Bitreader reader, int logAlphabetSize) throws IOException {
        splitExponent = reader.readBits(MathHelper.ceilLog1p(logAlphabetSize));
        if (this.splitExponent == logAlphabetSize) {
            msbInToken = this.lsbInToken = 0;
            return;
        }
        msbInToken = reader.readBits(MathHelper.ceilLog1p(splitExponent));
        if (msbInToken > splitExponent)
            throw new InvalidBitstreamException("msbInToken is too large");
        lsbInToken = reader.readBits(MathHelper.ceilLog1p(splitExponent - msbInToken));
        if (msbInToken + lsbInToken > splitExponent)
            throw new InvalidBitstreamException("msbInToken + lsbInToken is too large");
    }
}
