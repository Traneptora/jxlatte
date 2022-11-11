package com.thebombzen.jxlatte.frame.group;

import java.io.IOException;

import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.modular.GlobalModular;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.frame.vardct.HFMetadata;
import com.thebombzen.jxlatte.frame.vardct.LFCoefficients;
import com.thebombzen.jxlatte.io.Bitreader;

public class LFGroup {
    public final ModularStream modularLFGroup;
    public final LFCoefficients lfCoeff;
    public final HFMetadata hfCoeff;
    public LFGroup(Bitreader reader, Frame parent, int index, ModularChannelInfo[] replaced) throws IOException {
        GlobalModular gModular = parent.getLFGlobal().gModular;
        modularLFGroup = new ModularStream(reader, gModular.globalTree, parent,
            1 + parent.getNumLFGroups() + index, replaced);
        modularLFGroup.decodeChannels(reader, false);
        if (parent.getFrameHeader().encoding == FrameFlags.VARDCT) {
            this.lfCoeff = new LFCoefficients(reader, parent, 1 + index);
            this.hfCoeff = new HFMetadata(reader, parent, 1 + 2*parent.getNumLFGroups() + index);
        } else {
            this.lfCoeff = null;
            this.hfCoeff = null;
        }
    }
}
