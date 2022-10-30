package com.thebombzen.jxlatte.frame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.thebombzen.jxlatte.frame.lfglobal.GlobalModular;
import com.thebombzen.jxlatte.frame.modular.ModularChannel;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.io.Bitreader;

public class LFGroup {
    public final ModularStream lfStream;
    public final LFCoefficients lfCoeff;
    public final HFCoefficients hfCoeff;
    public final int[] replacedChannelIndices;
    public LFGroup(Bitreader reader, Frame parent, int index) throws IOException {
        GlobalModular globalModular = parent.getLFGlobal().gModular;
        List<ModularChannel> channels = new ArrayList<>();
        List<Integer> replacedChannelIndices = new ArrayList<>();
        for (int i = 0; i < globalModular.stream.getEncodedChannelCount(); i++) {
            ModularChannel chan = globalModular.stream.getChannel(i);
            if (!chan.isDecoded() && chan.hshift >= 3 && chan.vshift >= 3) {
                replacedChannelIndices.add(i);
                int width = parent.getFrameHeader().groupDim >> (chan.hshift - 3);
                int height = parent.getFrameHeader().groupDim >> (chan.vshift - 3);
                channels.add(new ModularChannel(width, height, chan.hshift, chan.vshift));
            }
        }
        this.replacedChannelIndices = replacedChannelIndices.stream().mapToInt(Integer::intValue).toArray();

        lfStream = new ModularStream(reader, globalModular.globalTree, parent, 1 + parent.getNumLFGroups() + index,
            channels.stream().toArray(ModularChannel[]::new));
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
