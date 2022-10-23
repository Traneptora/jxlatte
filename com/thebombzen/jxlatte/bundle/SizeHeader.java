package com.thebombzen.jxlatte.bundle;

import java.awt.Dimension;
import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.io.Bitreader;

public class SizeHeader extends Dimension {

    public SizeHeader(Bitreader reader, ImageHeader parent) throws IOException {
        boolean div8 = reader.readBool();
        if (div8)
            this.height = (1 + reader.readBits(5)) << 3;
        else
            this.height = reader.readU32(1, 9, 1, 13, 1, 18, 1, 30);
        int ratio = reader.readBits(3);
        if (ratio != 0) {
            this.width = getWidthFromRatio(ratio, this.height);
        } else {
            if (div8)
                this.width = (1 + reader.readBits(5)) << 3;
            else
                this.width = reader.readU32(1, 9, 1, 13, 1, 18, 1, 30);
        }
        
        long maxDim;
        long maxTimes;

        if (parent.getLevel() <= 5) {
            maxDim = 1L << 18;
            maxTimes = 1L << 30;
        } else {
            maxDim = 1L << 28;
            maxTimes = 1L << 40;
        }

        if (this.width > maxDim || this.height > maxDim)
            throw new InvalidBitstreamException(String.format("Width or height too large: %d, %d", this.width, this.height));
        if ((long)this.width * (long)this.height > maxTimes)
            throw new InvalidBitstreamException(String.format("Width times height too large: %d, %d", this.width, this.height));
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
}
