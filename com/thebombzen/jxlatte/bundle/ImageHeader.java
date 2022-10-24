package com.thebombzen.jxlatte.bundle;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.io.Bitreader;

public class ImageHeader {

    public static final int CODESTREAM_HEADER = 0x0AFF;

    private SizeHeader size;
    private int level = 5;
    private int orientation;
    private SizeHeader intrinsicSize = null;
    private PreviewHeader previewHeader = null;
    private AnimationHeader animationHeader = null;
    private BitDepthHeader bitDepth;
    private boolean modular16bitBuffers = true;
    private ExtraChannelInfo[] extraChannelInfo;
    private boolean xybEncoded = true;

    private ImageHeader() {

    }

    public static ImageHeader parse(Bitreader reader, int level) throws IOException {
        ImageHeader header = new ImageHeader();
        if (reader.readBits(16) != CODESTREAM_HEADER)
            throw new InvalidBitstreamException(String.format("Not a JXL Codestream: 0xFF0A magic mismatch"));
        header.setLevel(level);
        header.size = new SizeHeader(reader, level);

        boolean allDefault = reader.readBool();
        boolean extraFields = allDefault ? false : reader.readBool();

        if (extraFields) {
            header.orientation = 1 + reader.readBits(3);
            // have intrinsic size
            if (reader.readBool())
                header.intrinsicSize = new SizeHeader(reader, level);
            // have preview header
            if (reader.readBool())
                header.previewHeader = new PreviewHeader(reader);
            // have animation header
            if (reader.readBool())
                header.animationHeader = new AnimationHeader(reader);
        } else {
            header.orientation = 1;
        }

        if (allDefault) {
            header.bitDepth = new BitDepthHeader();
            header.modular16bitBuffers = true;
            header.extraChannelInfo = new ExtraChannelInfo[0];
            header.xybEncoded = true;
        } else {
            header.bitDepth = new BitDepthHeader(reader);
            header.modular16bitBuffers = reader.readBool();
            int extraChannelCount = reader.readU32(0, 0, 1, 0, 2, 4, 1, 12);
            header.extraChannelInfo = new ExtraChannelInfo[extraChannelCount];
            for (int i = 0; i < extraChannelCount; i++) {
                header.extraChannelInfo[i] = new ExtraChannelInfo(reader);
            }
            header.xybEncoded = reader.readBool();
        }

        return header;
    }

    public int getLevel() {
        return level;
    }

    public SizeHeader getSize() {
        return size;
    }

    public PreviewHeader getPreviewHeader() {
        return previewHeader;
    }

    public SizeHeader getIntrinsticSize() {
        return intrinsicSize;
    }

    public AnimationHeader getAnimationHeader() {
        return animationHeader;
    }

    public int getOrientation() {
        return orientation;
    }

    public BitDepthHeader getBitDepthHeader() {
        return bitDepth;
    }

    public boolean modularUses16BitBuffers() {
        return modular16bitBuffers;
    }

    public int getExtraChannelCount() {
        return extraChannelInfo.length;
    }

    public ExtraChannelInfo getExtraChannelInfo(int index) {
        return extraChannelInfo[index];
    }

    public boolean isXybEncoded() {
        return xybEncoded;
    }

    public void setLevel(int level) throws InvalidBitstreamException {
        if (level != 5 && level != 10)
            throw new InvalidBitstreamException();
        this.level = level;
    }
}
