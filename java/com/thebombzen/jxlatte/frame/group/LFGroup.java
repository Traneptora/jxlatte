package com.thebombzen.jxlatte.frame.group;

import java.io.IOException;

import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.frame.vardct.HFMetadata;
import com.thebombzen.jxlatte.frame.vardct.LFCoefficients;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.IntPoint;

public class LFGroup {
    public final int[][][] modularLFGroupBuffer;
    public final ModularChannelInfo[] modularLFGroupInfo;
    public final LFCoefficients lfCoeff;
    public final HFMetadata hfMetadata;
    public final int lfGroupID;
    public final Frame frame;
    public final IntPoint size;

    public LFGroup(Bitreader reader, Frame parent, int index, ModularChannelInfo[] replaced) throws IOException {
        this.lfGroupID = index;
        this.frame = parent;
        this.size = frame.getLFGroupSize(index).shiftRight(3);

        long time0 = System.nanoTime() / 1000000L;

        if (parent.getFrameHeader().encoding == FrameFlags.VARDCT)
            this.lfCoeff = new LFCoefficients(reader, this, parent);
        else
            this.lfCoeff = null;

        long time1 = System.nanoTime() / 1000000L;

        ModularStream modularLFGroup = new ModularStream(reader, frame, 1 + frame.getNumLFGroups() + lfGroupID, replaced);
        modularLFGroup.decodeChannels(reader);
        modularLFGroupBuffer = modularLFGroup.getDecodedBuffer();
        modularLFGroupInfo = new ModularChannelInfo[this.modularLFGroupBuffer.length];
        for (int c = 0; c < this.modularLFGroupInfo.length; c++)
            modularLFGroupInfo[c] =  new ModularChannelInfo(modularLFGroup.getChannelInfo(c));
        // free(modularLFGroup)
        modularLFGroup = null;

        long time2 = System.nanoTime() / 1000000L;

        if (parent.getFrameHeader().encoding == FrameFlags.VARDCT)
            this.hfMetadata = new HFMetadata(reader, this, parent);
        else
            this.hfMetadata = null;
        
        long time3 = System.nanoTime() / 1000000L;

        System.err.format("LF Group: %d%n    LFCoeff: %d%n    MLF: %d%n    HFMeta: %d%n", index, time1 - time0, time2 - time1, time3 - time2);
    }
}
