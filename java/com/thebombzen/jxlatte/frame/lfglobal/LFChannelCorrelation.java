package com.thebombzen.jxlatte.frame.lfglobal;

import java.io.IOException;
import java.util.Objects;

import com.thebombzen.jxlatte.io.Bitreader;

public class LFChannelCorrelation {
    public final int colorFactor;
    public final float baseCorrelationX;
    public final float baseCorrelationB;
    public final int xFactorLF;
    public final int bFactorLF;
    public LFChannelCorrelation(Bitreader reader) throws IOException {
        boolean allDefault = reader.readBool();
        if (allDefault) {
            colorFactor = 84;
            baseCorrelationX = 0.0f;
            baseCorrelationB = 1.0f;
            xFactorLF = 127;
            bFactorLF = 127;
        } else {
            colorFactor = reader.readU32(84, 0, 256, 0, 2, 8, 258, 16);
            baseCorrelationX = reader.readF16();
            baseCorrelationB = reader.readF16();
            xFactorLF = reader.readBits(8);
            bFactorLF = reader.readBits(8);
        }
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
    @Override
    public String toString() {
        return "LFChannelCorrelation [colorFactor=" + colorFactor + ", baseCorrelationX=" + baseCorrelationX
                + ", baseCorrelationB=" + baseCorrelationB + ", xFactorLF=" + xFactorLF + ", bFactorLF=" + bFactorLF
                + "]";
    }
    
}
