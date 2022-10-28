package com.thebombzen.jxlatte.frame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.thebombzen.jxlatte.MathHelper;
import com.thebombzen.jxlatte.bundle.Extensions;
import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.io.Bitreader;

public class FrameHeader {

    public final int type;
    public final int encoding;
    public final long flags;
    public final boolean doYCbCr;
    public final int[] jpegUpsampling;
    public final int upsampling;
    public final int[] ecUpsampling;
    public final int groupSizeShift;
    public final int groupDim;
    public final int xqmScale;
    public final int bqmScale;
    public final Passes passes;
    public final int lfLevel;
    public final boolean haveCrop;
    public final int x0;
    public final int y0;
    public final int width;
    public final int height;
    public final BlendingInfo blendingInfo;
    public final BlendingInfo[] ecBlendingInfo;
    public final int duration;
    public final int timecode;
    public final boolean isLast;
    public final int saveAsReference;
    public final boolean saveBeforeCT;
    public final String name;
    public final RestorationFilter restorationFilter;
    public final Extensions extensions;

    public FrameHeader(Bitreader reader, ImageHeader parent) throws IOException {
        boolean allDefault = reader.readBool();
        type = allDefault ? FrameFlags.REGULAR_FRAME : reader.readBits(2);
        encoding = allDefault ? FrameFlags.VARDCT : reader.readBits(1);
        flags = allDefault ? 0 : reader.readU64();
        doYCbCr = (!allDefault && !parent.isXYBEncoded()) ? reader.readBool() : false;
        jpegUpsampling = new int[3];
        if (doYCbCr && (flags & FrameFlags.USE_LF_FRAME) == 0) {
            for (int i = 0; i < 3; i++)
                jpegUpsampling[i] = reader.readBits(2);
        } else {
            Arrays.fill(jpegUpsampling, 1);
        }
        ecUpsampling = new int[parent.getExtraChannelCount()];
        if (!allDefault && (flags & FrameFlags.USE_LF_FRAME) == 0) {
            upsampling = 1 << reader.readBits(2);
            for (int i = 0; i < ecUpsampling.length; i++)
                ecUpsampling[i] = 1 << reader.readBits(2);
        } else {
            upsampling = 1;
            Arrays.fill(ecUpsampling, 1);
        }
        groupSizeShift = encoding == FrameFlags.MODULAR ? reader.readBits(2) : 1;
        groupDim = 128 << groupSizeShift;
        if (!allDefault && parent.isXYBEncoded() && encoding == FrameFlags.VARDCT) {
            xqmScale = reader.readBits(3);
            bqmScale = reader.readBits(3);
        } else {
            xqmScale = 3;
            bqmScale = 2;
        }
        passes = (!allDefault && type != FrameFlags.REFERENCE_ONLY) ? new Passes(reader) : new Passes();
        lfLevel = type == FrameFlags.LF_FRAME ? 1 + reader.readBits(2) : 0;
        haveCrop = (!allDefault && type != FrameFlags.LF_FRAME) ? reader.readBool() : false;
        if (haveCrop && type != FrameFlags.REFERENCE_ONLY) {
            int ux0 = reader.readU32(0, 8, 256, 11, 2304, 14, 18688, 30);
            int uy0 = reader.readU32(0, 8, 256, 11, 2304, 14, 18688, 30);
            x0 = MathHelper.unpackSigned(ux0);
            y0 = MathHelper.unpackSigned(uy0);
        } else {
            x0 = y0 = 0;
        }
        if (haveCrop) {
            width = reader.readU32(0, 8, 256, 11, 2304, 14, 18688, 30);
            height = reader.readU32(0, 8, 256, 11, 2304, 14, 18688, 30);
        } else {
            width = parent.getSize().width;
            height = parent.getSize().height;
        }
        boolean normalFrame = !allDefault && (type == FrameFlags.REGULAR_FRAME || type == FrameFlags.SKIP_PROGRESSIVE);
        boolean fullFrame = x0 <= 0 && y0 <= 0 && (width + x0) >= parent.getSize().width
            && (height + y0) >= parent.getSize().height;
        ecBlendingInfo = new BlendingInfo[parent.getExtraChannelCount()];
        if (normalFrame) {
            blendingInfo = new BlendingInfo(reader, ecBlendingInfo.length > 0, fullFrame);
            for (int i = 0; i < ecBlendingInfo.length; i++)
                ecBlendingInfo[i] = new BlendingInfo(reader, true, fullFrame);
        } else {
            blendingInfo = new BlendingInfo();
            Arrays.fill(ecBlendingInfo, blendingInfo);
        }
        duration = normalFrame && parent.getAnimationHeader() != null ?
            reader.readU32(0, 0, 1, 0, 0, 8, 0, 32) : 0;
        timecode = normalFrame && parent.getAnimationHeader() != null && parent.getAnimationHeader().have_timecodes ?
            reader.readBits(32) : 0;
        isLast = normalFrame ? reader.readBool() : type == FrameFlags.REGULAR_FRAME;
        saveAsReference = !allDefault && type != FrameFlags.LF_FRAME && !isLast ? reader.readBits(2) : 0;
        saveBeforeCT = !allDefault && (type == FrameFlags.REFERENCE_ONLY || fullFrame
            && (type == FrameFlags.REGULAR_FRAME || type == FrameFlags.SKIP_PROGRESSIVE)
            && (duration == 0 || saveAsReference != 0)
            && !isLast && blendingInfo.mode == FrameFlags.BLEND_REPLACE)
                ? reader.readBool() : false;
        if (allDefault) {
            name = "";
        } else {
            int nameLen = reader.readU32(0, 0, 0, 4, 16, 5, 48, 10);
            byte[] buffer = new byte[nameLen];
            for (int i = 0; i < nameLen; i++)
                buffer[i] = (byte)reader.readBits(8);
            name = new String(buffer, StandardCharsets.UTF_8);
        }
        restorationFilter = allDefault ? new RestorationFilter() : new RestorationFilter(reader, encoding);
        extensions = allDefault ? new Extensions() : new Extensions(reader);
    }
}
