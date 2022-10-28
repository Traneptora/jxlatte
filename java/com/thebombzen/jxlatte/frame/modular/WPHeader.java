package com.thebombzen.jxlatte.frame.modular;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

public class WPHeader {
    public final int wp_p1;
    public final int wp_p2;
    public final int wp_p3a;
    public final int wp_p3b;
    public final int wp_p3c;
    public final int wp_p3d;
    public final int wp_p3e;
    public final int[] wp_w = new int[4];

    public WPHeader(Bitreader reader) throws IOException {
        if (reader.readBool()) {
            wp_p1 = 16;
            wp_p2 = 10;
            wp_p3a = wp_p3b = wp_p3c = 7;
            wp_p3d = wp_p3e = 0;
            wp_w[0] = 13;
            wp_w[1] = wp_w[2] = wp_w[3] = 12;
        } else {
            wp_p1 = reader.readBits(5);
            wp_p2 = reader.readBits(5);
            wp_p3a = reader.readBits(5);
            wp_p3b = reader.readBits(5);
            wp_p3c = reader.readBits(5);
            wp_p3d = reader.readBits(5);
            wp_p3e = reader.readBits(5);
            wp_w[0] = reader.readBits(4);
            wp_w[1] = reader.readBits(4);
            wp_w[2] = reader.readBits(4);
            wp_w[3] = reader.readBits(4);
        }
    }
}
