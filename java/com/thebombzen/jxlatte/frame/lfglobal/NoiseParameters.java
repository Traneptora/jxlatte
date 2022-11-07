package com.thebombzen.jxlatte.frame.lfglobal;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

public class NoiseParameters {

    public final int[] lut = new int[8];

    public NoiseParameters(Bitreader reader) throws IOException {
        for (int i = 0; i < lut.length; i++)
            lut[i] = reader.readBits(10);
    }
}