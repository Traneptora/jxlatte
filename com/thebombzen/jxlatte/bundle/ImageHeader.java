package com.thebombzen.jxlatte.bundle;

import java.io.IOException;

import com.thebombzen.jxlatte.Bitreader;
import com.thebombzen.jxlatte.InvalidBitstreamException;

public class ImageHeader {

    public static final int CODESTREAM_HEADER = 0x0AFF;

    private SizeHeader size;
    private int level = 5;

    private ImageHeader() {

    }

    public static ImageHeader parse(Bitreader reader) throws IOException, InvalidBitstreamException {
        ImageHeader header = new ImageHeader();
        if (reader.readBits(16) != CODESTREAM_HEADER)
            throw new InvalidBitstreamException("Not a JXL Codestream: 0xFF0A magic mismatch");
        header.size = SizeHeader.parse(reader, header);

        

        return header;
    }

    public int getLevel() {
        return level;
    }

    public SizeHeader getSize() {
        return size;
    }

    public void setLevel(int level) throws InvalidBitstreamException {
        if (level != 5 && level != 10)
            throw new InvalidBitstreamException();
        this.level = level;
    }
}
