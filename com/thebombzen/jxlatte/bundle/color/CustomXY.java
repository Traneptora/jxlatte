package com.thebombzen.jxlatte.bundle.color;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

public class CustomXY extends CIEXY {

    public static int unpackSigned(int value) {
        // prevent overflow and extra casework
        long v = (long)value & 0xFF_FF_FF_FFL;
        return (int)((v & 1L) == 0 ? v / 2L : -(v + 1L) / 2L);
    }

    // hack to deal with Java's super() mechanics
    private static CIEXY readCustom(Bitreader reader) throws IOException {
        int ux = reader.readU32(0, 19, 524288, 19, 1048576, 20, 2097152, 21);
        int uy = reader.readU32(0, 19, 524288, 19, 1048576, 20, 2097152, 21);
        float x = unpackSigned(ux) * 1e-6f;
        float y = unpackSigned(uy) * 1e-6f;
        return new CIEXY(x, y);
    }

    public CustomXY(Bitreader reader) throws IOException {
        super(readCustom(reader));
    }
}
