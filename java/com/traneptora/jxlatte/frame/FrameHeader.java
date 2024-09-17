package com.traneptora.jxlatte.frame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.IntStream;

import com.traneptora.jxlatte.bundle.BlendingInfo;
import com.traneptora.jxlatte.bundle.Extensions;
import com.traneptora.jxlatte.bundle.ImageHeader;
import com.traneptora.jxlatte.bundle.PassesInfo;
import com.traneptora.jxlatte.frame.features.RestorationFilter;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.Dimension;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.Point;
import com.traneptora.jxlatte.util.Rectangle;

public class FrameHeader {

    public final int type;
    public final int encoding;
    public final long flags;
    public final boolean doYCbCr;
    public final int[] jpegUpsamplingY;
    public final int[] jpegUpsamplingX;
    public final int upsampling;
    public final int[] ecUpsampling;
    public final int groupSizeShift;
    public final int groupDim;
    public final int lfGroupDim;
    public final int logGroupDim;
    public final int logLFGroupDim;
    public final int xqmScale;
    public final int bqmScale;
    public final PassesInfo passes;
    public final int lfLevel;
    public final boolean haveCrop;
    public Rectangle bounds;
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

    public FrameHeader(FrameHeader header) {
        this.type = header.type;
        this.encoding = header.encoding;
        this.flags = header.flags;
        this.doYCbCr = header.doYCbCr;
        this.jpegUpsamplingY = header.jpegUpsamplingY;
        this.jpegUpsamplingX = header.jpegUpsamplingX;
        this.upsampling = header.upsampling;
        this.ecUpsampling = header.ecUpsampling;
        this.groupSizeShift = header.groupSizeShift;
        this.groupDim = header.groupDim;
        this.lfGroupDim = header.lfGroupDim;
        this.logGroupDim = header.logGroupDim;
        this.logLFGroupDim = header.logLFGroupDim;
        this.xqmScale = header.xqmScale;
        this.bqmScale = header.bqmScale;
        this.passes = header.passes;
        this.lfLevel = header.lfLevel;
        this.haveCrop = header.haveCrop;
        this.bounds = new Rectangle(header.bounds);
        this.blendingInfo =  header.blendingInfo;
        this.ecBlendingInfo = header.ecBlendingInfo;
        this.duration = header.duration;
        this.timecode = header.timecode;
        this.isLast = header.isLast;
        this.saveAsReference = header.saveAsReference;
        this.saveBeforeCT = header.saveBeforeCT;
        this.name = header.name;
        this.restorationFilter = header.restorationFilter;
        this.extensions = header.extensions;
    }

    public FrameHeader(Bitreader reader, ImageHeader parent) throws IOException {
        boolean allDefault = reader.readBool();
        type = allDefault ? FrameFlags.REGULAR_FRAME : reader.readBits(2);
        encoding = allDefault ? FrameFlags.VARDCT : reader.readBits(1);
        flags = allDefault ? 0 : reader.readU64();
        doYCbCr = (!allDefault && !parent.isXYBEncoded()) ? reader.readBool() : false;
        jpegUpsamplingY = new int[3];
        jpegUpsamplingX = new int[3];
        if (doYCbCr && (flags & FrameFlags.USE_LF_FRAME) == 0) {
            for (int i = 0; i < 3; i++) {
                int mode = reader.readBits(2);
                switch (mode) {
                    case 1:
                        jpegUpsamplingY[i] = 1;
                        jpegUpsamplingX[i] = 1;
                        break;
                    case 2:
                        jpegUpsamplingY[i] = 0;
                        jpegUpsamplingX[i] = 1;
                        break;
                    case 3:
                        jpegUpsamplingY[i] = 1;
                        jpegUpsamplingX[i] = 0;
                        break;
                    default:
                        break;
                }
            }
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
        lfGroupDim = groupDim << 3;
        logGroupDim = MathHelper.ceilLog2(groupDim);
        logLFGroupDim = MathHelper.ceilLog2(lfGroupDim);
        if (parent.isXYBEncoded() && encoding == FrameFlags.VARDCT) {
            if (!allDefault) {
                xqmScale = reader.readBits(3);
                bqmScale = reader.readBits(3);
            } else {
                xqmScale = 3;
                bqmScale = 2;
            }
        } else {
            xqmScale = 2;
            bqmScale = 2;
        }
        passes = (!allDefault && type != FrameFlags.REFERENCE_ONLY) ? new PassesInfo(reader) : new PassesInfo();
        lfLevel = type == FrameFlags.LF_FRAME ? 1 + reader.readBits(2) : 0;
        haveCrop = (!allDefault && type != FrameFlags.LF_FRAME) ? reader.readBool() : false;
        bounds = new Rectangle();
        Dimension imageSize = parent.getSize();
        if (haveCrop && type != FrameFlags.REFERENCE_ONLY) {
            int x0 = reader.readU32(0, 8, 256, 11, 2304, 14, 18688, 30);
            int y0 = reader.readU32(0, 8, 256, 11, 2304, 14, 18688, 30);
            bounds.origin.x = MathHelper.unpackSigned(x0);
            bounds.origin.y = MathHelper.unpackSigned(y0);
        }
        if (haveCrop) {
            bounds.size.width = reader.readU32(0, 8, 256, 11, 2304, 14, 18688, 30);
            bounds.size.height = reader.readU32(0, 8, 256, 11, 2304, 14, 18688, 30);
        } else {
            bounds.size = new Dimension(imageSize);
        }
        boolean normalFrame = !allDefault && (type == FrameFlags.REGULAR_FRAME || type == FrameFlags.SKIP_PROGRESSIVE);
        Point lowerCorner = bounds.computeLowerCorner();
        boolean fullFrame = bounds.origin.y <= 0 && bounds.origin.x <= 0
            && lowerCorner.y >= imageSize.height
            && lowerCorner.x >= imageSize.width;
        // intentionally computed after fullFrame
        bounds.size.height = MathHelper.ceilDiv(bounds.size.height, upsampling);
        bounds.size.width = MathHelper.ceilDiv(bounds.size.width, upsampling);
        bounds.size.height = MathHelper.ceilDiv(bounds.size.height, 1 << (3 * lfLevel));
        bounds.size.width = MathHelper.ceilDiv(bounds.size.width, 1 << (3 * lfLevel));
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
        int maxJPY = IntStream.of(jpegUpsamplingY).reduce(Math::max).getAsInt();
        int maxJPX = IntStream.of(jpegUpsamplingX).reduce(Math::max).getAsInt();
        bounds.size.height = MathHelper.ceilDiv(bounds.size.height, 1 << maxJPY) << maxJPY;
        bounds.size.width = MathHelper.ceilDiv(bounds.size.width, 1 << maxJPX) << maxJPX;
        for (int i = 0; i < 3; i++) {
            jpegUpsamplingY[i] = maxJPY - jpegUpsamplingY[i];
            jpegUpsamplingX[i] = maxJPX - jpegUpsamplingX[i];
        }
    }

    @Override
    public String toString() {
        return String.format(
                "FrameHeader [type=%s, encoding=%s, flags=%s, doYCbCr=%s, jpegUpsamplingY=%s, jpegUpsamplingX=%s, upsampling=%s, ecUpsampling=%s, groupSizeShift=%s, groupDim=%s, lfGroupDim=%s, logGroupDim=%s, logLFGroupDim=%s, xqmScale=%s, bqmScale=%s, passes=%s, lfLevel=%s, haveCrop=%s, bounds=%s, blendingInfo=%s, ecBlendingInfo=%s, duration=%s, timecode=%s, isLast=%s, saveAsReference=%s, saveBeforeCT=%s, name=%s, restorationFilter=%s, extensions=%s]",
                type, encoding, flags, doYCbCr, Arrays.toString(jpegUpsamplingY), Arrays.toString(jpegUpsamplingX),
                upsampling, Arrays.toString(ecUpsampling), groupSizeShift, groupDim, lfGroupDim, logGroupDim,
                logLFGroupDim, xqmScale, bqmScale, passes, lfLevel, haveCrop, bounds, blendingInfo,
                Arrays.toString(ecBlendingInfo), duration, timecode, isLast, saveAsReference, saveBeforeCT, name,
                restorationFilter, extensions);
    }
}
