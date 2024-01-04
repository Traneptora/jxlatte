package com.traneptora.jxlatte.frame.vardct;

import java.io.IOException;

import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.functional.FunctionalHelper;

public class LFChannelCorrelation {
    public final int colorFactor;
    public final float baseCorrelationX;
    public final float baseCorrelationB;
    public final int xFactorLF;
    public final int bFactorLF;

    private LFChannelCorrelation(Bitreader reader, boolean allDefault) {
        if (allDefault) {
            colorFactor = 84;
            baseCorrelationX = 0.0f;
            baseCorrelationB = 1.0f;
            xFactorLF = 128;
            bFactorLF = 128;
        } else {
            try {
                colorFactor = reader.readU32(84, 0, 256, 0, 2, 8, 258, 16);
                baseCorrelationX = reader.readF16();
                baseCorrelationB = reader.readF16();
                xFactorLF = reader.readBits(8);
                bFactorLF = reader.readBits(8);
            } catch (IOException ex) {
                FunctionalHelper.sneakyThrow(ex);
                // prevent the compiler from whining about final fields
                throw null;
            }
        }
    }

    public LFChannelCorrelation() {
        this(null, true);
    }

    public LFChannelCorrelation(Bitreader reader) throws IOException {
        this(reader, reader.readBool());
    }   
}
