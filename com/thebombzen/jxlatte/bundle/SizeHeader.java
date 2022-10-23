package com.thebombzen.jxlatte.bundle;

import java.awt.Dimension;
import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

public class SizeHeader extends Dimension {

    private SizeHeader() {

    }

    public static int getWidthFromRatio(int ratio, int height) {
        switch (ratio) {
            case 1:
                return height;
            case 2:
                return (int)(height * 12L / 10L);
            case 3:
                return (int)(height * 4L / 3L);
            case 4:
                return (int)(height * 3L / 2L);
            case 5:
                return (int)(height * 16L / 9L);
            case 6:
                return (int)(height * 5L / 4L);
            case 7:
                return height * 2;
            default:
                throw new IllegalArgumentException();
        }
    }

    public static SizeHeader parse(Bitreader reader, ImageHeader parent) throws IOException {
        SizeHeader header = new SizeHeader();
        boolean div8 = reader.readBool();
        if (div8)
            header.height = (1 + reader.readBits(5)) << 3;
        else
            header.height = reader.readU32(1, 9, 1, 13, 1, 18, 1, 30);
        int ratio = reader.readBits(3);
        if (ratio != 0) {
            header.width = getWidthFromRatio(ratio, header.height);
        } else {
            if (div8)
                header.width = (1 + reader.readBits(5)) << 3;
            else
                header.width = reader.readU32(1, 9, 1, 13, 1, 18, 1, 30);
        }
        // TODO check level limits
        return header;
    }
}
