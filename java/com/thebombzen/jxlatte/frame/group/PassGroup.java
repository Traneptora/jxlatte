package com.thebombzen.jxlatte.frame.group;

import java.io.IOException;

import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.modular.GlobalModular;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.frame.vardct.HFCoefficients;
import com.thebombzen.jxlatte.io.Bitreader;

public class PassGroup {
    public final HFCoefficients hfCoefficients;
    public final ModularStream stream;
    public PassGroup(Bitreader reader, Frame frame, int pass, int group,
            ModularChannelInfo[] replacedChannels) throws IOException {
        if (frame.getFrameHeader().encoding == FrameFlags.VARDCT)
            hfCoefficients = new HFCoefficients(reader, frame, pass, group);
        else
            hfCoefficients = null;
        GlobalModular globalModular = frame.getLFGlobal().gModular;
        stream = new ModularStream(reader, globalModular.globalTree, frame,
            18 + 3 * frame.getNumLFGroups() + frame.getNumGroups() * pass + group, replacedChannels);
        stream.decodeChannels(reader, false);
    }
}
