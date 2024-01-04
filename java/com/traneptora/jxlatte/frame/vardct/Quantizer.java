package com.traneptora.jxlatte.frame.vardct;

import java.io.IOException;

import com.traneptora.jxlatte.io.Bitreader;

public class Quantizer {
    public final int globalScale;
    public final int quantLF;
    public final float[] scaledDequant = new float[3];
    public Quantizer(Bitreader reader, float[] lfDequant) throws IOException {
        this.globalScale = reader.readU32(1, 11, 2049, 11, 4097, 12, 8193, 16);
        this.quantLF = reader.readU32(16, 0, 1, 5, 1, 8, 1, 16);
        for (int i = 0; i < 3; i++) {
            scaledDequant[i] =  (1 << 16) * lfDequant[i] / (globalScale * quantLF);
        }
    }
}
