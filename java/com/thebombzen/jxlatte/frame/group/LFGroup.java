package com.thebombzen.jxlatte.frame.group;

import java.io.IOException;

import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.frame.vardct.HFBlockContext;
import com.thebombzen.jxlatte.frame.vardct.HFMetadata;
import com.thebombzen.jxlatte.frame.vardct.LFCoefficients;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.IntPoint;

public class LFGroup {
    public final ModularStream modularLFGroup;
    public final LFCoefficients lfCoeff;
    public final HFMetadata hfMetadata;
    public final int lfGroupID;
    public final Frame frame;
    public final int[][] lfIndex;
    public final IntPoint size;

    public LFGroup(Bitreader reader, Frame parent, int index, ModularChannelInfo[] replaced) throws IOException {
        this.lfGroupID = index;
        this.frame = parent;
        this.size = frame.getLFGroupSize(index).shiftRight(3);

        modularLFGroup = new ModularStream(reader, frame, 1 + frame.getNumLFGroups() + lfGroupID, replaced);
        modularLFGroup.decodeChannels(reader);

        HFBlockContext hfctx = frame.getLFGlobal().hfBlockCtx;
        if (parent.getFrameHeader().encoding == FrameFlags.VARDCT) {
            this.lfCoeff = new LFCoefficients(reader, this, parent);
            this.hfMetadata = new HFMetadata(reader, this, parent);
            this.lfIndex = new int[size.y][size.x];
            for (int y = 0; y < size.y; y++) {
                for (int x = 0; x < size.x; x++) {
                    lfIndex[y][x] = getLFIndex(hfctx, new IntPoint(x, y), frame.getFrameHeader().jpegUpsampling);
                }
            }
        } else {
            this.lfCoeff = null;
            this.hfMetadata = null;
            this.lfIndex = null;
        }
    }

    private int getLFIndex(HFBlockContext hfctx, IntPoint blockPos, IntPoint[] upsampling) {
        int[][][] lfBuff = lfCoeff.lfQuant.getDecodedBuffer();
        int[] c = new int[]{1, 0, 2};
        int[] index = new int[3];

        for (int i = 0; i < 3; i++) {
            IntPoint shifted = blockPos.shiftLeft(upsampling[i].negate());
            for (int t : hfctx.lfThresholds[i]) {
                if (lfBuff[c[i]][shifted.y][shifted.x] > t) {
                    index[i]++;
                }
            }
        }

        int lfIndex = index[0];
        lfIndex *= hfctx.lfThresholds[2].length + 1;
        lfIndex += index[2];
        lfIndex *= hfctx.lfThresholds[1].length + 1;
        lfIndex += index[1];

        return lfIndex;
    }
}
