package com.traneptora.jxlatte.bundle;

import java.io.IOException;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.io.Bitreader;

public class SizeHeader {

    public final int width;
    public final int height;

    public SizeHeader(Bitreader reader, int level) throws IOException {
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

        long maxDim = level <=5 ? 1L << 18 : 1L << 28;
        long maxTimes = level <= 5 ? 1L << 30 : 1L << 40;

        if (this.width > maxDim || this.height > maxDim)
            throw new InvalidBitstreamException(String.format("Width or height too large: %d, %d",
                this.width, this.height));
        if ((long)this.width * (long)this.height > maxTimes)
            throw new InvalidBitstreamException(String.format("Width times height too large: %d, %d",
                this.width, this.height));
    }

    public static int getWidthFromRatio(int ratio, int height) {
        switch (ratio) {
            case 1:
                return height;
            case 2:
                return (int)(height * 6L / 5L);
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
