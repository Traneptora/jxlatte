package com.thebombzen.jxlatte;

import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.bundle.color.CIEPrimaries;
import com.thebombzen.jxlatte.bundle.color.CIEXY;
import com.thebombzen.jxlatte.bundle.color.ColorEncoding;
import com.thebombzen.jxlatte.bundle.color.ColorEncodingBundle;
import com.thebombzen.jxlatte.bundle.color.Primaries;
import com.thebombzen.jxlatte.bundle.color.TransferFunction;
import com.thebombzen.jxlatte.bundle.color.WhitePoint;

import java.util.function.DoubleUnaryOperator;

public class JXLImage {
    private double[][][] buffer;
    private ImageHeader imageHeader;
    private ColorEncoding colorEncoding;

    private int primaries;
    private int whitePoint;
    private int transfer;

    private CIEXY white1931;
    private CIEPrimaries primaries1931;

    public JXLImage(double[][][] buffer, ImageHeader header) {
        this.buffer = buffer;
        this.imageHeader = header;
        if (header.isXYBEncoded()) {
            this.primaries = Primaries.SRGB;
            this.whitePoint = WhitePoint.D65;
            this.transfer = TransferFunction.LINEAR;
            this.primaries1931 = Primaries.getPrimaries(primaries);
            this.white1931 = WhitePoint.getWhitePoint(whietPoint);
        } else {
            ColorEncodingBundle bundle = header.getColorEncoding();
            this.primaries = bundle.primaries;
            this.whitePoint = bundle.whitePoint;
            this.transfer = bundle.tf;
            this.primaries1931 = bundle.prim;
            this.white1931 = bundle.white;
        }
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

    public void transfer(int transfer) {
        if (transfer == this.transfer)
            return;
        DoubleUnaryOperator inverse = TransferFunction.getInverseTransferFunction(this.transfer);
        DoubleUnaryOperator forward = TransferFunction.getTransferFunction(transfer);
        DoubleUnaryOperator composed = inverse.andThen(forward);
        TaskList<?> tasks = new TaskList<>();
        int width = getWidth();
        int height = getHeight();
        for (int c_ = 0; c_ < buffer.length; c_++) {
            final int c = c_;
            for (int y_ = 0; y_ < height; y_++) {
                final int y = y_;
                tasks.submit(() -> {
                    for (int x = 0; x < width; x++)
                        buffer[c][y][x] = composed.applyAsDouble(buffer[c][y][x]);
                });
            }
        }
        tasks.collect();
        this.transfer = transfer;
    }
}
