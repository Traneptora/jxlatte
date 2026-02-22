package com.traneptora.jxlatte.frame.group;

import java.io.IOException;

import com.traneptora.jxlatte.bundle.PassesInfo;
import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.FrameFlags;
import com.traneptora.jxlatte.frame.modular.ModularChannel;
import com.traneptora.jxlatte.frame.modular.ModularStream;
import com.traneptora.jxlatte.frame.vardct.HFPass;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.MathHelper;

public class Pass {

    public final int minShift;
    public final int maxShift;
    public final ModularChannel[] replacedChannels;
    public final HFPass hfPass;

    public Pass(Bitreader reader, Frame frame, int passIndex, int prevMinshift) throws IOException {
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
        ModularStream stream = frame.getLFGlobal().globalModular;
        replacedChannels = new ModularChannel[stream.getEncodedChannelCount()];
        for (int i = 0; i < replacedChannels.length; i++) {
            ModularChannel chan = stream.getChannel(i);
            if (!chan.isDecoded()) {
                int m = Math.min(chan.vshift, chan.hshift);
                if (minShift <= m && m < maxShift)
                    replacedChannels[i] = new ModularChannel(chan);
            }
        }

        if (frame.getFrameHeader().encoding == FrameFlags.VARDCT)
            hfPass = new HFPass(reader, frame, passIndex);
        else
            hfPass = null;
    }
}
