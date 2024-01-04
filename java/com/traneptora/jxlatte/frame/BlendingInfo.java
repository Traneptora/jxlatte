package com.traneptora.jxlatte.frame;

import java.io.IOException;
import java.util.Objects;

import com.traneptora.jxlatte.io.Bitreader;

public class BlendingInfo {
    public final int mode;
    public final int alphaChannel;
    public final boolean clamp;
    public final int source;

    public BlendingInfo() {
        this(0, 0, false, 0);
    }

    public BlendingInfo(int mode, int alphaChannel, boolean clamp, int source) {
        this.mode = mode;
        this.alphaChannel = alphaChannel;
        this.clamp = clamp;
        this.source = source;
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(mode, alphaChannel, clamp, source);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BlendingInfo other = (BlendingInfo) obj;
        return mode == other.mode && alphaChannel == other.alphaChannel && clamp == other.clamp
                && source == other.source;
    }

    @Override
    public String toString() {
        return "BlendingInfo [mode=" + mode + ", alphaChannel=" + alphaChannel + ", clamp=" + clamp + ", source="
                + source + "]";
    }
}
