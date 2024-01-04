package com.thebombzen.jxlatte.frame.modular;

import java.io.IOException;
import java.util.Arrays;

import com.thebombzen.jxlatte.io.Bitreader;

public class TransformInfo {
    public static final int RCT = 0;
    public static final int PALETTE = 1;
    public static final int SQUEEZE = 2;

    public final int tr;
    public final int beginC;
    public final int rctType;
    public final int numC;
    public final int nbColors;
    public final int nbDeltas;
    public final int dPred;
    public final SqueezeParam[] sp;

    public TransformInfo(Bitreader reader) throws IOException {
        tr = reader.readBits(2);
        if (tr != SQUEEZE)
            beginC = reader.readU32(0, 3, 8, 6, 72, 10, 1096, 13);
        else
            beginC = 0;
        if (tr == RCT)
            rctType = reader.readU32(6, 0, 0, 2, 2, 4, 10, 6);
        else
            rctType = 0;
        if (tr == PALETTE) {
            numC = reader.readU32(1, 0, 3, 0, 4, 0, 1, 13);
            nbColors = reader.readU32(0, 8, 256, 10, 1280, 12, 5376, 16);
            nbDeltas = reader.readU32(0, 0, 1, 8, 257, 10, 1281, 16);
            dPred = reader.readBits(4);
        } else {
            numC = nbColors = nbDeltas = dPred = 0;
        }
        if (tr == SQUEEZE) {
            int numSq = reader.readU32(0, 0, 1, 4, 9, 6, 41, 8);
            sp = new SqueezeParam[numSq];
            for (int i = 0; i < numSq; i++)
                sp[i] = new SqueezeParam(reader);
        } else {
            sp = null;
        }
    }

    @Override
    public String toString() {
        return String.format(
                "TransformInfo [tr=%s, beginC=%s, rctType=%s, numC=%s, nbColors=%s, nbDeltas=%s, dPred=%s, sp=%s]", tr,
                beginC, rctType, numC, nbColors, nbDeltas, dPred, Arrays.toString(sp));
    }
}
