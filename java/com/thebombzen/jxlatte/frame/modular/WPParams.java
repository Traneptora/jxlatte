package com.thebombzen.jxlatte.frame.modular;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

public class WPParams {
    public final int param1;
    public final int param2;
    public final int param3a;
    public final int param3b;
    public final int param3c;
    public final int param3d;
    public final int param3e;
    public final int[] weight = new int[4];

    public WPParams(Bitreader reader) throws IOException {
        if (reader.readBool()) {
            param1 = 16;
            param2 = 10;
            param3a = param3b = param3c = 7;
            param3d = param3e = 0;
            weight[0] = 13;
            weight[1] = weight[2] = weight[3] = 12;
        } else {
            param1 = reader.readBits(5);
            param2 = reader.readBits(5);
            param3a = reader.readBits(5);
            param3b = reader.readBits(5);
            param3c = reader.readBits(5);
            param3d = reader.readBits(5);
            param3e = reader.readBits(5);
            weight[0] = reader.readBits(4);
            weight[1] = reader.readBits(4);
            weight[2] = reader.readBits(4);
            weight[3] = reader.readBits(4);
        }
    }
}
