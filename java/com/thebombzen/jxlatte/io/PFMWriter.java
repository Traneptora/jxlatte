package com.thebombzen.jxlatte.io;

import java.awt.image.Raster;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.thebombzen.jxlatte.JXLImage;
import com.thebombzen.jxlatte.color.ColorFlags;

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
        int width = image.getWidth();
        int height = image.getHeight();
        String header = String.format("%s\n%d %d\n1.0\n", gray ? "Pf" : "PF",
            image.getWidth(), image.getHeight());
        dout.writeBytes(header);
        Raster raster = image.asBufferedImage().getRaster();
        // pfm is in backwards scanline order, bottom to top
        for (int y = height - 1; y >= 0; y--) {
             for (int x = 0; x < width; x++) {
                for (int c = 0; c < (gray ? 1 : 3); c++)
                    dout.writeFloat(raster.getSampleFloat(x, y, c));
            }
        }
    }
}
