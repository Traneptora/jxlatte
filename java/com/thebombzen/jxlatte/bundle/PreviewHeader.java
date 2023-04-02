package com.thebombzen.jxlatte.bundle;

import java.awt.Dimension;
import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.io.Bitreader;

public class PreviewHeader extends Dimension {

    public PreviewHeader(Bitreader reader) throws IOException {
        boolean div8 = reader.readBool();
        if (div8)
            this.height = reader.readU32(16, 0, 32, 0, 1, 5, 33, 9);
        else
            this.height = reader.readU32(1, 6, 65, 8, 321, 10, 1345, 12);
        int ratio = reader.readBits(3);
        if (ratio != 0) {
            this.width = SizeHeader.getWidthFromRatio(ratio, this.height);
        } else {
            if (div8)
                this.width = reader.readU32(16, 0, 32, 0, 1, 5, 33, 9);
            else
                this.width = reader.readU32(1, 6, 65, 8, 321, 10, 1345, 12);
        }

        if (this.width > 4096 || this.height > 4096)
            throw new InvalidBitstreamException(String.format(
                "preview width or preview height too large: %d, %d", this.width, this.height));
    }
}
