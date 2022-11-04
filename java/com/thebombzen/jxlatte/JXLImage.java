package com.thebombzen.jxlatte;

import java.util.function.DoubleUnaryOperator;

import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.bundle.color.CIEPrimaries;
import com.thebombzen.jxlatte.bundle.color.CIEXY;
import com.thebombzen.jxlatte.bundle.color.ColorEncoding;
import com.thebombzen.jxlatte.bundle.color.ColorEncodingBundle;
import com.thebombzen.jxlatte.bundle.color.Primaries;
import com.thebombzen.jxlatte.bundle.color.TransferFunction;
import com.thebombzen.jxlatte.bundle.color.WhitePoint;
import com.thebombzen.jxlatte.util.TaskList;

public class JXLImage {
    private double[][][] buffer;
    private ImageHeader imageHeader;
    private int colorEncoding;
    private int alphaIndex;

    private int primaries;
    private int whitePoint;
    private int transfer;

    private CIEXY white1931;
    private CIEPrimaries primaries1931;

    public JXLImage(double[][][] buffer, ImageHeader header) {
        this.buffer = buffer;
        this.colorEncoding = buffer.length >= 3 ? ColorEncoding.RGB : ColorEncoding.GRAY;
        this.alphaIndex = header.hasAlpha() ? header.getAlphaIndex(0) : -1;
        this.imageHeader = header;
        if (header.isXYBEncoded()) {
            this.primaries = Primaries.SRGB;
            this.whitePoint = WhitePoint.D65;
            this.transfer = TransferFunction.LINEAR;
            this.primaries1931 = Primaries.getPrimaries(primaries);
            this.white1931 = WhitePoint.getWhitePoint(whitePoint);
        } else {
            ColorEncodingBundle bundle = header.getColorEncoding();
            this.primaries = bundle.primaries;
            this.whitePoint = bundle.whitePoint;
            this.transfer = bundle.tf;
            this.primaries1931 = bundle.prim;
            this.white1931 = bundle.white;
        }
    }

    public JXLImage(JXLImage image) {
        buffer = new double[image.buffer.length][][];
        for (int c = 0; c < buffer.length; c++) {
            buffer[c] = new double[image.buffer[c].length][];
            for (int y = 0; y < buffer[c].length; y++) {
                this.buffer[c][y] = new double[image.buffer[c][y].length];
                System.arraycopy(image.buffer[c][y], 0, buffer[c][y], 0, buffer[c][y].length);
            }
        }
        this.colorEncoding = image.colorEncoding;
        this.alphaIndex = image.alphaIndex;
        this.imageHeader = image.imageHeader;
        this.primaries = image.primaries;
        this.whitePoint = image.whitePoint;
        this.transfer = image.transfer;
        this.primaries1931 = image.primaries1931;
        this.white1931 = image.white1931;
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

    public JXLImage transfer(int transfer) {
        if (transfer == this.transfer)
            return this;
        JXLImage image = new JXLImage(this);
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
                        image.buffer[c][y][x] = composed.applyAsDouble(buffer[c][y][x]);
                });
            }
        }
        tasks.collect();
        image.transfer = transfer;
        return image;
    }

    public CIEPrimaries getCIEPrimaries() {
        return this.primaries1931;
    }

    public CIEXY getCIEWhitePoint() {
        return this.white1931;
    }

    public int getPrimaries() {
        return primaries;
    }

    public int getWhitePoint() {
        return whitePoint;
    }

    public int getTransfer() {
        return transfer;
    }

    public int getColorEncoding() {
        return this.colorEncoding;
    }

    public boolean hasAlpha() {
        return alphaIndex >= 0;
    }

    public int getAlphaIndex() {
        return alphaIndex;
    }
}
