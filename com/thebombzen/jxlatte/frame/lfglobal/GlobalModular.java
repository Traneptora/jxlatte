package com.thebombzen.jxlatte.frame.lfglobal;

import java.io.IOException;

import com.thebombzen.jxlatte.bundle.color.ColorSpace;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.FrameHeader;
import com.thebombzen.jxlatte.frame.modular.MATree;
import com.thebombzen.jxlatte.io.Bitreader;

public class GlobalModular {
    public final MATree globalTree;
    public final Frame frame;
    
    private float[][] decodedChannels;

    public GlobalModular(Bitreader reader, Frame parent) throws IOException {
        frame = parent;
        boolean hasGlobalTree = reader.readBool();
        if (hasGlobalTree) {
            globalTree = new MATree(reader);
        } else {
            globalTree = null;
        }
        int subModularChannelCount = frame.globalMetadata.getExtraChannelCount();
        FrameHeader header = frame.getFrameHeader();
        if (header.encoding == FrameFlags.MODULAR) {
            if (!header.doYCbCr && !frame.globalMetadata.isXYBEncoded()
                    && frame.globalMetadata.getColorEncoding().colorSpace == ColorSpace.GRAY)
                subModularChannelCount += 1;
            else
                subModularChannelCount += 3;
        }
        decodedChannels = new float[subModularChannelCount][];
    }
}
