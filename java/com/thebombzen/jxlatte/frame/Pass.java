package com.thebombzen.jxlatte.frame;

import java.util.ArrayList;
import java.util.List;

import com.thebombzen.jxlatte.frame.lfglobal.GlobalModular;
import com.thebombzen.jxlatte.frame.modular.ModularChannel;
import com.thebombzen.jxlatte.util.MathHelper;

public class Pass {

    public final int minShift;
    public final int maxShift;
    public final int[] replacedChannelIndices;
    public final ModularChannel[] replacedChannels;

    public Pass(Frame frame, int passIndex, int prevMinshift) {
        maxShift = passIndex > 0 ? prevMinshift : 3;
        int n = -1;
        PassesInfo passes = frame.getFrameHeader().passes;
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
                    channels.add(new ModularChannel(chan.width, chan.height, chan.hshift, chan.vshift));
                }
            }
        }

        this.replacedChannelIndices = replacedChannelIndices.stream().mapToInt(Integer::intValue).toArray();
        this.replacedChannels = channels.stream().toArray(ModularChannel[]::new);
    }
}
