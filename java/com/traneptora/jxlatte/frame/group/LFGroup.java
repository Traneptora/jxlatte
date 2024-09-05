package com.traneptora.jxlatte.frame.group;

import java.io.IOException;

import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.FrameFlags;
import com.traneptora.jxlatte.frame.modular.ModularChannel;
import com.traneptora.jxlatte.frame.modular.ModularStream;
import com.traneptora.jxlatte.frame.vardct.HFMetadata;
import com.traneptora.jxlatte.frame.vardct.LFCoefficients;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.Dimension;
import com.traneptora.jxlatte.util.ImageBuffer;

public class LFGroup {

    public final LFCoefficients lfCoeff;
    public final HFMetadata hfMetadata;
    public final int lfGroupID;
    public final Frame frame;
    public final Dimension size;
    public final ModularStream modularLFGroup;

    public LFGroup(Bitreader reader, Frame parent, int index, ModularChannel[] replaced,
            ImageBuffer[] lfBuffer) throws IOException {
        this.lfGroupID = index;
        this.frame = parent;
        Dimension pixelSize = frame.getLFGroupSize(lfGroupID);
        size = new Dimension(pixelSize.height >> 3, pixelSize.width >> 3);
        if (parent.getFrameHeader().encoding == FrameFlags.VARDCT)
            this.lfCoeff = new LFCoefficients(reader, this, parent, lfBuffer);
        else
            this.lfCoeff = null;

        modularLFGroup = new ModularStream(reader, frame, 1 + frame.getNumLFGroups() +
            lfGroupID, replaced);
        modularLFGroup.decodeChannels(reader);

        if (parent.getFrameHeader().encoding == FrameFlags.VARDCT)
            this.hfMetadata = new HFMetadata(reader, this, parent);
        else
            this.hfMetadata = null;       
    }
}
