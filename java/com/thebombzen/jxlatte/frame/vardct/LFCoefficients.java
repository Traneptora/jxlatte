package com.thebombzen.jxlatte.frame.vardct;

import java.io.IOException;

import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.FrameHeader;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.MathHelper;

public class LFCoefficients {
    public final int extraPrecision;
    public final ModularStream lfQuant;

    public LFCoefficients(Bitreader reader, Frame frame, int streamIndex) throws IOException {
        if ((frame.getFrameHeader().flags & FrameFlags.USE_LF_FRAME) != 0) {
            System.err.println("TODO: Implement LF Frames");
            throw new UnsupportedOperationException("LF Frames currently not implemented");
        }
        this.extraPrecision = reader.readBits(2);
        FrameHeader header = frame.getFrameHeader();
        int width = MathHelper.ceilDiv(header.width, 8);
        int height = MathHelper.ceilDiv(header.height, 8);
        ModularChannelInfo[] info = new ModularChannelInfo[3];
        for (int i = 0; i < 3; i++) {
            info[i] = new ModularChannelInfo(width >> header.jpegUpsamplingX[i],
                height >> header.jpegUpsamplingY[i], 0, 0);
        }
        this.lfQuant = new ModularStream(reader, frame.getLFGlobal().gModular.globalTree, frame, streamIndex, info);
        lfQuant.decodeChannels(reader, false);
    }
}
