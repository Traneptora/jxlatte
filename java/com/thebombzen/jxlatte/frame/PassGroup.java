package com.thebombzen.jxlatte.frame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.thebombzen.jxlatte.MathHelper;
import com.thebombzen.jxlatte.frame.lfglobal.GlobalModular;
import com.thebombzen.jxlatte.frame.modular.ModularChannel;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.io.Bitreader;

public class PassGroup {
    public final HFCoefficients hfCoefficients;
    public final int[] replacedChannelIndices;
    public final int minShift;
    public final int maxShift;
    public final ModularStream stream;
    public PassGroup(Bitreader reader, Frame frame, int passIndex, int groupIndex, int prevMinshift) throws IOException {
        if (frame.getFrameHeader().encoding == FrameFlags.VARDCT)
            hfCoefficients = new HFCoefficients(reader);
        else
            hfCoefficients = null;
        maxShift = passIndex > 0 ? prevMinshift : 3;
        int n = -1;
        Passes passes = frame.getFrameHeader().passes;
        for (int i = 0; i < passes.lastPass.length; i++) {
            if (passes.lastPass[i] == passIndex) {
                n = i;
                break;
            }
        }
        minShift = n >= 0 ? MathHelper.ceilLog1p(passes.downSample[n] - 1) : maxShift;
        GlobalModular globalModular = frame.getLFGlobal().gModular;
        List<ModularChannel> channels = new ArrayList<>();
        List<Integer> replacedChannelIndices = new ArrayList<>();
        for (int i = 0; i < globalModular.stream.getEncodedChannelCount(); i++) {
            ModularChannel chan = globalModular.stream.getChannel(i);
            if (!chan.isDecoded()) {
                int m = Math.min(chan.hshift, chan.vshift);
                if (minShift <= m && m < maxShift) {
                    replacedChannelIndices.add(i);
                    int width = frame.getFrameHeader().groupDim >> chan.hshift;
                    int height = frame.getFrameHeader().groupDim >> chan.vshift;
                    channels.add(new ModularChannel(width, height, chan.hshift, chan.vshift));
                }
            }
        }
        this.replacedChannelIndices = replacedChannelIndices.stream().mapToInt(Integer::intValue).toArray();
        stream = new ModularStream(reader, globalModular.globalTree, frame,
            18 + 3 * frame.getNumLFGroups() + frame.getNumGroups() * passIndex + groupIndex,
            channels.stream().toArray(ModularChannel[]::new));
        stream.decodeChannels(reader, false);
        stream.applyTransforms();
    }
}
