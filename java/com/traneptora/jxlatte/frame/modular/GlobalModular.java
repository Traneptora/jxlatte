package com.traneptora.jxlatte.frame.modular;

import java.io.IOException;

import com.traneptora.jxlatte.color.ColorFlags;
import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.FrameFlags;
import com.traneptora.jxlatte.frame.FrameHeader;
import com.traneptora.jxlatte.io.Bitreader;

public class GlobalModular {
    public final MATree globalTree;
    public final Frame frame;
    public final ModularStream stream;

    public GlobalModular(Bitreader reader, Frame parent) throws IOException {
        frame = parent;
        boolean hasGlobalTree = reader.readBool();
        if (hasGlobalTree) {
            globalTree = new MATree(parent.getLoggers(), reader);
        } else {
            globalTree = null;
        }
        frame.setGlobalTree(globalTree);
        int subModularChannelCount = frame.globalMetadata.getExtraChannelCount();
        FrameHeader header = frame.getFrameHeader();
        int ecStart = 0;
        if (header.encoding == FrameFlags.MODULAR) {
            if (!header.doYCbCr && !frame.globalMetadata.isXYBEncoded()
                    && frame.globalMetadata.getColorEncoding().colorEncoding == ColorFlags.CE_GRAY)
                ecStart = 1;
            else
                ecStart = 3;
        }
        subModularChannelCount += ecStart;
        stream = new ModularStream(reader, parent, 0, subModularChannelCount, ecStart);
        stream.decodeChannels(reader, true);
    }
}
