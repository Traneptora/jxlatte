package com.thebombzen.jxlatte.image;

import com.thebombzen.jxlatte.bundle.ImageHeader;

public class JXLImage {
    private double[][][] buffer;
    private ImageHeader imageHeader;

    public JXLImage(double[][][] buffer, ImageHeader header) {
        this.buffer = buffer;
        this.imageHeader = header;
    }

    public ImageHeader getHeader() {
        return imageHeader;
    }

    public int getWidth() {
        return imageHeader.getSize().width;
    }

    public int getHeight() {
        return imageHeader.getSize().height;
    }

    public double[][][] getBuffer() {
        return buffer;
    }
}
