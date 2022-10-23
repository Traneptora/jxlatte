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

    private ImageHeader() {

    }

    public static ImageHeader parse(Bitreader reader, int level) throws IOException {
        ImageHeader header = new ImageHeader();
        if (reader.readBits(16) != CODESTREAM_HEADER)
            throw new InvalidBitstreamException(String.format("Not a JXL Codestream: 0xFF0A magic mismatch"));
        header.setLevel(level);
        header.size = new SizeHeader(reader, header);

        boolean allDefault = reader.readBool();
        boolean extraFields = allDefault ? false : reader.readBool();

        if (extraFields) {
            header.orientation = 1 + reader.readBits(3);
            // have intrinsic size
            if (reader.readBool())
                header.intrinsicSize = new SizeHeader(reader, header);
            // have preview header
            if (reader.readBool())
                header.previewHeader = new PreviewHeader(reader, header);
            // have animation header
            if (reader.readBool())
                header.animationHeader = new AnimationHeader(reader, header);
            
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

    public void setLevel(int level) throws InvalidBitstreamException {
        if (level != 5 && level != 10)
            throw new InvalidBitstreamException();
        this.level = level;
    }
}
