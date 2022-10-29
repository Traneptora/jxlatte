package com.thebombzen.jxlatte.frame.lfglobal;

import java.io.IOException;
import java.util.Objects;

import com.thebombzen.jxlatte.bundle.color.ColorSpace;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.FrameHeader;
import com.thebombzen.jxlatte.frame.modular.MATree;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.io.Bitreader;

public class GlobalModular {
    public final MATree globalTree;
    public final Frame frame;
    public final ModularStream stream;
    
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
        int ecStart = 0;
        if (header.encoding == FrameFlags.MODULAR) {
            if (!header.doYCbCr && !frame.globalMetadata.isXYBEncoded()
                    && frame.globalMetadata.getColorEncoding().colorSpace == ColorSpace.GRAY)
                ecStart = 1;
            else
                ecStart = 3;
        }
        subModularChannelCount += ecStart;
        stream = new ModularStream(reader, globalTree, parent, 0, subModularChannelCount, ecStart);
        stream.decodeChannels(reader, true);
    }

    @Override
    public int hashCode() {
        return Objects.hash(globalTree, frame, stream);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        GlobalModular other = (GlobalModular) obj;
        return Objects.equals(globalTree, other.globalTree) && Objects.equals(frame, other.frame)
                && Objects.equals(stream, other.stream);
    }

    @Override
    public String toString() {
        return "GlobalModular [globalTree=" + globalTree + ", frame=" + frame + ", stream=" + stream + "]";
    }
}
