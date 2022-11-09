package com.thebombzen.jxlatte.color;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.MathHelper;

public class CustomXY extends CIEXY {

    // hack to deal with Java's super() mechanics
    private static CIEXY readCustom(Bitreader reader) throws IOException {
        int ux = reader.readU32(0, 19, 524288, 19, 1048576, 20, 2097152, 21);
        int uy = reader.readU32(0, 19, 524288, 19, 1048576, 20, 2097152, 21);
        float x = MathHelper.unpackSigned(ux) * 1e-6f;
        float y = MathHelper.unpackSigned(uy) * 1e-6f;
        return new CIEXY(x, y);
    }

    public CustomXY(Bitreader reader) throws IOException {
        super(readCustom(reader));
    }
}
