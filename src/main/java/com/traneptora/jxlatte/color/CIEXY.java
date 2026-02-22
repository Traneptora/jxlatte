package com.traneptora.jxlatte.color;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.MathHelper;

public class CIEXY implements Serializable {

    private static final long serialVersionUID = 0xb3c642d8db60fd9aL;

    public static CIEXY readCustom(Bitreader reader) throws IOException {
        int ux = reader.readU32(0, 19, 524288, 19, 1048576, 20, 2097152, 21);
        int uy = reader.readU32(0, 19, 524288, 19, 1048576, 20, 2097152, 21);
        float x = MathHelper.unpackSigned(ux) * 1e-6f;
        float y = MathHelper.unpackSigned(uy) * 1e-6f;
        return new CIEXY(x, y);
    }

    public static boolean matches(CIEXY a, CIEXY b) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        return a.matches(b);
    }

    public final float x;
    public final float y;

    public CIEXY(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public CIEXY(CIEXY xy) {
        this(xy.x, xy.y);
    }

    public boolean matches(CIEXY xy) {
        return Math.abs(x - xy.x) + Math.abs(y - xy.y) < 1e-4f;
    }

    @Override
    public String toString() {
        return String.format("CIEXY [x=%s, y=%s]", x, y);
    }

    public boolean equals(Object another) {
        if (another == null || !another.getClass().equals(this.getClass()))
            return false;
        CIEXY other = (CIEXY)another;
        return Float.floatToRawIntBits(x) == Float.floatToRawIntBits(other.x)
            && Float.floatToRawIntBits(y) == Float.floatToRawIntBits(other.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Float.hashCode(x), Float.hashCode(y));
    }
}
