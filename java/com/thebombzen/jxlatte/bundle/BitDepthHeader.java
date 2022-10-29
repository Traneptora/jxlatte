package com.thebombzen.jxlatte.bundle;

import java.io.IOException;
import java.util.Objects;

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

    @Override
    public int hashCode() {
        return Objects.hash(usesFloatSamples, bitsPerSample, expBits);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BitDepthHeader other = (BitDepthHeader) obj;
        return usesFloatSamples == other.usesFloatSamples && bitsPerSample == other.bitsPerSample
                && expBits == other.expBits;
    }

    @Override
    public String toString() {
        return "BitDepthHeader [usesFloatSamples=" + usesFloatSamples + ", bitsPerSample=" + bitsPerSample
                + ", expBits=" + expBits + "]";
    }
}
