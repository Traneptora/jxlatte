package com.thebombzen.jxlatte.frame.modular;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

public class SqueezeParam {
    public final boolean horizontal;
    public final boolean inPlace;
    public final int beginC;
    public final int numC;

    public SqueezeParam(Bitreader reader) throws IOException {
        this.horizontal = reader.readBool();
        this.inPlace = reader.readBool();
        this.beginC = reader.readU32(0, 3, 8, 6, 72, 10, 1096, 13);
        this.numC = reader.readU32(1, 0, 2, 0, 3, 0, 4, 4);
    }

    public SqueezeParam(boolean horizontal, boolean inPlace, int beginC, int numC) {
        this.horizontal = horizontal;
        this.inPlace = inPlace;
        this.beginC = beginC;
        this.numC = numC;
    }

    @Override
    public String toString() {
        return String.format("SqueezeParam [horizontal=%s, inPlace=%s, beginC=%s, numC=%s]", horizontal, inPlace,
                beginC, numC);
    }
}
