package com.thebombzen.jxlatte.frame.group;

import java.io.IOException;

import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.modular.GlobalModular;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.frame.vardct.HFCoefficients;
import com.thebombzen.jxlatte.frame.vardct.LFCoefficients;
import com.thebombzen.jxlatte.io.Bitreader;

public class LFGroup {
    public final ModularStream lfStream;
    public final LFCoefficients lfCoeff;
    public final HFCoefficients hfCoeff;
    public LFGroup(Bitreader reader, Frame parent, int index, ModularChannelInfo[] replaced) throws IOException {
        GlobalModular gModular = parent.getLFGlobal().gModular;
        lfStream = new ModularStream(reader, gModular.globalTree, parent,
            1 + parent.getNumLFGroups() + index, replaced);
        lfStream.decodeChannels(reader, false);
        lfStream.applyTransforms();
        if (parent.getFrameHeader().encoding == FrameFlags.VARDCT) {
            this.lfCoeff = new LFCoefficients(reader);
            this.hfCoeff = new HFCoefficients(reader);
            throw new UnsupportedOperationException("VarDCT currently not implemented");
        } else {
            this.lfCoeff = null;
            this.hfCoeff = null;
        }
    }
}
