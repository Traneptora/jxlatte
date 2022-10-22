package com.thebombzen.jxlatte;

import java.awt.image.BufferedImage;
import java.io.IOException;

import com.thebombzen.jxlatte.bundle.ImageHeader;

public class JXLDecoder {
    private Bitreader bitreader;
    private ImageHeader imageHeader;

    public JXLDecoder(Bitreader in) {
        this.bitreader = in;
    }

    public BufferedImage decode() throws IOException {
        this.imageHeader = ImageHeader.parse(bitreader);
        int width = imageHeader.getSize().width;
        int height = imageHeader.getSize().height;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        return image;
    }
}
