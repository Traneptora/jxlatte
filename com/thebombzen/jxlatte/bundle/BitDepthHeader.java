package com.thebombzen.jxlatte.bundle;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

public class BitDepthHeader {
    public final boolean usesFloatSamples;
    public final int bitsPerSample;
    public final int expBits;

    public BitDepthHeader() {
        this.usesFloatSamples = false;
        this.bitsPerSample = 8;
        this.expBits = 0;
    }

    public BitDepthHeader(Bitreader reader) throws IOException {
        this.usesFloatSamples = reader.readBool();
        if (this.usesFloatSamples) {
            this.bitsPerSample = reader.readU32(32, 0, 16, 0, 24, 0, 1, 6);
            this.expBits = 1 + reader.readBits(4);
        } else {
            this.bitsPerSample = reader.readU32(8, 0, 10, 0, 12, 0, 1, 6);
            this.expBits = 0;
        }
    }
}
