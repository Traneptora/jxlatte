package com.traneptora.jxlatte.frame.features;

import java.io.IOException;
import java.util.Arrays;

import com.traneptora.jxlatte.bundle.BlendingInfo;
import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.InvalidBitstreamException;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.Point;
import com.traneptora.jxlatte.util.Rectangle;


public class Patch {
    public final int ref;
    public final Rectangle bounds;
    public final Point[] positions;
    public final BlendingInfo[][] blendingInfos;

    public Patch(EntropyStream stream, Bitreader reader, int extraChannelCount,
            int alphaChannelCount) throws IOException {
        ref = stream.readSymbol(reader, 1);
        int x = stream.readSymbol(reader, 3);
        int y = stream.readSymbol(reader, 3);
        int width = 1 + stream.readSymbol(reader, 2);
        int height = 1 + stream.readSymbol(reader, 2);
        bounds = new Rectangle(y, x, height, width);
        int count = 1 + stream.readSymbol(reader, 7);
        if (count <= 0)
            throw new InvalidBitstreamException("That's a lot of patches!");
        positions = new Point[count];
        blendingInfos = new BlendingInfo[count][];
        for (int j = 0; j < count; j++) {
            if (j == 0) {
                x = stream.readSymbol(reader, 4);
                y = stream.readSymbol(reader, 4);
            } else {
                x = stream.readSymbol(reader, 6);
                y = stream.readSymbol(reader, 6);
                x = MathHelper.unpackSigned(x) + positions[j - 1].x;
                y = MathHelper.unpackSigned(y) + positions[j - 1].y;
            }
            positions[j] = new Point(y, x);
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
        return String.format("Patch [ref=%s, bounds=%s, positions=%s, blendingInfos=%s]", ref, bounds,
                Arrays.toString(positions), Arrays.toString(blendingInfos));
    }
}
