package com.traneptora.jxlatte.frame.vardct;

import java.io.IOException;
import java.util.Objects;

import com.traneptora.jxlatte.io.Bitreader;

public class LFChannelCorrelation {
    public final int colorFactor;
    public final float baseCorrelationX;
    public final float baseCorrelationB;
    public final int xFactorLF;
    public final int bFactorLF;

    public LFChannelCorrelation(int colorFactor, float baseCorrelationX,
        float baseCorrelationB, int xFactorLF, int bFactorLF) {
        this.colorFactor = colorFactor;
        this.baseCorrelationX = baseCorrelationX;
        this.baseCorrelationB = baseCorrelationB;
        this.xFactorLF = xFactorLF;
        this.bFactorLF = bFactorLF;
    }

    public LFChannelCorrelation() {
        this.colorFactor = 84;
        this.baseCorrelationX = 0.0f;
        this.baseCorrelationB = 1.0f;
        this.xFactorLF = 128;
        this.bFactorLF = 128;
    }

    public static LFChannelCorrelation read(Bitreader reader) throws IOException {
        if (reader.readBool())
            return new LFChannelCorrelation();
        int colorFactor = reader.readU32(84, 0, 256, 0, 2, 8, 258, 16);
        float baseCorrelationX = reader.readF16();
        float baseCorrelationB = reader.readF16();
        int xFactorLF = reader.readBits(8);
        int bFactorLF = reader.readBits(8);
        return new LFChannelCorrelation(colorFactor, baseCorrelationX, baseCorrelationB, xFactorLF, bFactorLF);
    }

    @Override
    public String toString() {
        return String.format(
                "LFChannelCorrelation [colorFactor=%s, baseCorrelationX=%s, baseCorrelationB=%s, xFactorLF=%s, bFactorLF=%s]",
                colorFactor, baseCorrelationX, baseCorrelationB, xFactorLF, bFactorLF);
    }

    @Override
    public int hashCode() {
        return Objects.hash(colorFactor, baseCorrelationX, baseCorrelationB, xFactorLF, bFactorLF);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LFChannelCorrelation other = (LFChannelCorrelation) obj;
        return colorFactor == other.colorFactor
                && Float.floatToIntBits(baseCorrelationX) == Float.floatToIntBits(other.baseCorrelationX)
                && Float.floatToIntBits(baseCorrelationB) == Float.floatToIntBits(other.baseCorrelationB)
                && xFactorLF == other.xFactorLF && bFactorLF == other.bFactorLF;
    }

}
