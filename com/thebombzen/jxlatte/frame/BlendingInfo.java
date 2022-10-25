package com.thebombzen.jxlatte.bundle;

import java.io.IOException;
import java.util.Objects;

import com.thebombzen.jxlatte.io.Bitreader;

public class BlendingInfo {
    public final int mode;
    public final int alphaChannel;
    public final boolean clamp;
    public final int source;

    public BlendingInfo(Bitreader reader, boolean extra, boolean fullFrame) throws IOException {
        mode = reader.readU32(0, 0, 1, 0, 2, 0, 3, 2);
        if (extra && (mode == FrameFlags.BLEND_BLEND || mode == FrameFlags.BLEND_MULADD))
            alphaChannel = reader.readU32(0, 0, 1, 0, 2, 0, 3, 3);
        else
            alphaChannel = 0;
        
        if (extra && (mode == FrameFlags.BLEND_BLEND
                || mode == FrameFlags.BLEND_MULT
                || mode == FrameFlags.BLEND_MULADD))
            clamp = reader.readBool();
        else
            clamp = false;
        if (mode != FrameFlags.BLEND_REPLACE || !fullFrame)
            source = reader.readBits(2);
        else
            source = 0;
    }

    public BlendingInfo() {
        mode = 0;
        alphaChannel = 0;
        clamp = false;
        source = 0;
    }

    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (other == null)
            return false;
        if (!this.getClass().equals(other.getClass()))
            return false;
        BlendingInfo o = (BlendingInfo)other;
        return o.mode == this.mode
            && o.alphaChannel == this.alphaChannel
            && o.clamp == this.clamp
            && o.source == this.source;
    }

    public int hashCode() {
        return Objects.hash(mode, alphaChannel, clamp, source);
    }
}
