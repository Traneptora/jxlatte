package com.traneptora.jxlatte.frame.features;

import java.io.IOException;
import java.util.Arrays;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.frame.BlendingInfo;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.IntPoint;
import com.traneptora.jxlatte.util.MathHelper;


public class Patch {
    public final int width;
    public final int height;
    public final int ref;
    public final IntPoint origin;
    public final IntPoint[] positions;
    public final BlendingInfo[][] blendingInfos;

    public Patch(EntropyStream stream, Bitreader reader, int extraChannelCount, int alphaChannelCount) throws IOException {
        this.ref = stream.readSymbol(reader, 1);
        int x0 = stream.readSymbol(reader, 3);
        int y0 = stream.readSymbol(reader, 3);
        this.origin = new IntPoint(x0, y0);
        this.width = 1 + stream.readSymbol(reader, 2);
        this.height = 1 + stream.readSymbol(reader, 2);
        int count = 1 + stream.readSymbol(reader, 7);
        if (count <= 0)
            throw new InvalidBitstreamException("That's a lot of patches!");
        positions = new IntPoint[count];
        blendingInfos = new BlendingInfo[count][];
        for (int j = 0; j < count; j++) {
            IntPoint xy;
            if (j == 0) {
                int x = stream.readSymbol(reader, 4);
                int y = stream.readSymbol(reader, 4);
                xy = new IntPoint(x, y);
            } else {
                int x = stream.readSymbol(reader, 6);
                int y = stream.readSymbol(reader, 6);
                x = MathHelper.unpackSigned(x) + positions[j - 1].x;
                y = MathHelper.unpackSigned(y) + positions[j - 1].y;
                xy = new IntPoint(x, y);
            }
            positions[j] = xy;
            blendingInfos[j] = new BlendingInfo[extraChannelCount + 1];
            for (int k = 0; k < extraChannelCount + 1; k++) {
                int mode = stream.readSymbol(reader, 5);
                int alpha = 0;
                boolean clamp = false;
                if (mode >= 8)
                    throw new InvalidBitstreamException("Illegal blending mode in patch");
                if (mode > 3 && alphaChannelCount > 1) {
                    alpha = stream.readSymbol(reader, 8);
                    if (alpha >= extraChannelCount)
                        throw new InvalidBitstreamException("Alpha out of bounds");
                }
                if (mode > 2)
                    clamp = stream.readSymbol(reader, 9) != 0;
                blendingInfos[j][k] = new BlendingInfo(mode, alpha, clamp, 0);
            }
        }
    }

    @Override
    public String toString() {
        return String.format("Patch [width=%s, height=%s, ref=%s, origin=%s, positions=%s, blendingInfos=%s]",
             width, height, ref, origin, Arrays.toString(positions), Arrays.deepToString(blendingInfos));
    }
}
