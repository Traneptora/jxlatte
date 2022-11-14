package com.thebombzen.jxlatte.frame.features.noise;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

public class NoiseParameters {

    public final double[] lut = new double[8];

    public NoiseParameters(Bitreader reader) throws IOException {
        for (int i = 0; i < lut.length; i++)
            lut[i] = reader.readBits(10) / 1024D;
    }
}
