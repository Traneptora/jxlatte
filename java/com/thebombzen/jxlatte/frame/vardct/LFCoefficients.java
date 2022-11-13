package com.thebombzen.jxlatte.frame.vardct;

import java.io.IOException;

import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.FrameHeader;
import com.thebombzen.jxlatte.frame.group.LFGroup;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.IntPoint;

public class LFCoefficients {
    public final int extraPrecision;
    public final ModularStream lfQuant;

    public LFCoefficients(Bitreader reader, LFGroup parent, Frame frame, int streamIndex) throws IOException {
        if ((frame.getFrameHeader().flags & FrameFlags.USE_LF_FRAME) != 0) {
            System.err.println("TODO: Implement LF Frames");
            throw new UnsupportedOperationException("LF Frames currently not implemented");
        }
        IntPoint size = frame.getLFGroupSize(parent.lfGroupID);
        this.extraPrecision = reader.readBits(2);
        FrameHeader header = frame.getFrameHeader();
        ModularChannelInfo[] info = new ModularChannelInfo[3];
        int[] c = new int[]{1, 0, 2};
        for (int i = 0; i < 3; i++) {
            int hshift = header.jpegUpsampling[i].x;
            int vshift = header.jpegUpsampling[i].y;
            int width = size.x >> (3 + hshift);
            int height = size.y >> (3 + vshift);
            info[c[i]] = new ModularChannelInfo(width, height, hshift, vshift);
        }
        this.lfQuant = new ModularStream(reader, frame.getLFGlobal().gModular.globalTree, frame, streamIndex, info);
        lfQuant.decodeChannels(reader, false);
    }
}
