package com.thebombzen.jxlatte.frame.lfglobal;

import java.io.IOException;

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
}
