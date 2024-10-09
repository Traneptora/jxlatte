package com.traneptora.jxlatte.io;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import com.traneptora.jxlatte.JXLImage;
import com.traneptora.jxlatte.color.ColorFlags;
import com.traneptora.jxlatte.util.ImageBuffer;

public class PFMWriter {
    private final JXLImage image;
    private boolean gray;

    public PFMWriter(JXLImage image) {
        this.image = image;
        this.gray = image.getColorEncoding() == ColorFlags.CE_GRAY;
    }

    public void write(OutputStream out) throws IOException {
        DataOutputStream dout = new DataOutputStream(out);
        // PFM spec requires \n here, not \r\n, so no %n
        int height = image.getHeight();
        int width = image.getWidth();
        byte[] header = String.format("%s\n%d %d\n1.0\n", gray ? "Pf" : "PF", width, height)
            .getBytes(StandardCharsets.US_ASCII);
        dout.write(header);
        ImageBuffer[] buffer = image.getBuffer(false);
        ImageBuffer[] nb = new ImageBuffer[buffer.length];
        for (int c = 0; c < nb.length; c++) {
            if (buffer[c].isInt()) {
                nb[c] = new ImageBuffer(buffer[c]);
                nb[c].castToFloatBuffer(~(~0 << image.getTaggedBitDepth(c)));
            } else {
                nb[c] = buffer[c];
            }
        }
        float[][][] b = Stream.of(nb).map(a -> a.getFloatBuffer()).toArray(float[][][]::new);
        int cCount = gray ? 1 : 3;
        // pfm is in backwards scanline order, bottom to top
        for (int y = height - 1; y >= 0; y--) {
             for (int x = 0; x < width; x++) {
                for (int c = 0; c < cCount; c++)
                    dout.writeFloat(b[c][y][x]);
            }
        }
    }
}
