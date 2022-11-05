package com.thebombzen.jxlatte;

import java.util.function.DoubleUnaryOperator;

import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.bundle.color.CIEPrimaries;
import com.thebombzen.jxlatte.bundle.color.CIEXY;
import com.thebombzen.jxlatte.bundle.color.ColorEncodingBundle;
import com.thebombzen.jxlatte.bundle.color.ColorFlags;
import com.thebombzen.jxlatte.util.ColorManagement;
import com.thebombzen.jxlatte.util.MathHelper;
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
        this.colorEncoding = buffer.length >= 3 ? ColorFlags.CE_RGB : ColorFlags.CE_GRAY;
        this.alphaIndex = header.hasAlpha() ? header.getAlphaIndex(0) : -1;
        this.imageHeader = header;
        if (header.isXYBEncoded()) {
            this.primaries = ColorFlags.PRI_SRGB;
            this.whitePoint = ColorFlags.WP_D65;
            this.transfer = ColorFlags.TF_LINEAR;
            this.primaries1931 = ColorFlags.getPrimaries(primaries);
            this.white1931 = ColorFlags.getWhitePoint(whitePoint);
        } else {
            ColorEncodingBundle bundle = header.getColorEncoding();
            this.primaries = bundle.primaries;
            this.whitePoint = bundle.whitePoint;
            this.transfer = bundle.tf;
            this.primaries1931 = bundle.prim;
            this.white1931 = bundle.white;
        }
    }

    private JXLImage(JXLImage image, boolean copyBuffer) {
        this.colorEncoding = image.colorEncoding;
        this.alphaIndex = image.alphaIndex;
        this.imageHeader = image.imageHeader;
        this.primaries = image.primaries;
        this.whitePoint = image.whitePoint;
        this.transfer = image.transfer;
        this.primaries1931 = image.primaries1931;
        this.white1931 = image.white1931;
        if (copyBuffer) {
            buffer = new double[image.buffer.length][][];
            for (int c = 0; c < buffer.length; c++) {
                buffer[c] = new double[image.buffer[c].length][];
                for (int y = 0; y < buffer[c].length; y++) {
                    this.buffer[c][y] = new double[image.buffer[c][y].length];
                    System.arraycopy(image.buffer[c][y], 0, buffer[c][y], 0, buffer[c][y].length);
                }
            }
        }
    }

    public JXLImage(JXLImage image) {
        this(image, true);
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

    private JXLImage toneMapLinear(CIEPrimaries primaries, CIEXY whitePoint) {
        if (this.primaries1931.matches(primaries) && this.white1931.matches(whitePoint))
            return this;
        double[][] conversionMatrix = ColorManagement.getConversionMatrix(primaries, whitePoint, this.primaries1931, this.white1931);
        TaskList<?> tasks = new TaskList<>();
        int width = getWidth();
        int height = getHeight();
        JXLImage image = new JXLImage(this);
        for (int y_ = 0; y_ < height; y_++) {
            final int y = y_;
            tasks.submit(() -> {
                for (int x = 0; x < width; x++) {
                    double[] rgb = new double[]{buffer[0][y][x], buffer[1][y][x], buffer[2][y][x]};
                    double[] rgb2 = MathHelper.matrixMutliply(conversionMatrix, rgb);
                    image.buffer[0][y][x] = rgb2[0];
                    image.buffer[1][y][x] = rgb2[1];
                    image.buffer[2][y][x] = rgb2[2];
                }
            });
        }
        tasks.collect();
        image.primaries1931 = primaries;
        image.white1931 = whitePoint;
        image.primaries = ColorFlags.getPrimaries(primaries);
        image.whitePoint = ColorFlags.getWhitePoint(whitePoint);
        return image;
    }

    public JXLImage fillColor() {
        if (this.colorEncoding != ColorFlags.CE_GRAY)
            return this;
        int w = getWidth();
        int h = getHeight();
        double[][][] newBuffer = new double[buffer.length + 2][h][w];
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < h; y++) {
                System.arraycopy(buffer[0][y], 0, newBuffer[c][y], 0, w);
            }
        }
        JXLImage image = new JXLImage(this, false);
        image.buffer = newBuffer;
        image.colorEncoding = ColorFlags.CE_RGB;
        return image;
    }

    public JXLImage transform(CIEPrimaries primaries, CIEXY whitePoint, int transfer) {
        if (this.primaries1931.matches(primaries) && this.white1931.matches(whitePoint))
            return this.transfer(transfer);
        return this.transfer(ColorFlags.TF_LINEAR)
            .fillColor()
            .toneMapLinear(primaries, whitePoint)
            .transfer(transfer);
    }

    public JXLImage transform(int primaries, int whitePoint, int transfer) {
        return transform(ColorFlags.getPrimaries(primaries), ColorFlags.getWhitePoint(whitePoint), transfer);
    }

    public JXLImage toneMap(CIEPrimaries primaries, CIEXY whitePoint) {
        return transform(primaries, whitePoint, this.transfer);
    }

    public JXLImage toneMap(int primaries, int whitePoint) {
        return transform(primaries, whitePoint, this.transfer);
    }

    public JXLImage transfer(int transfer) {
        if (transfer == this.transfer)
            return this;
        JXLImage image = new JXLImage(this);
        DoubleUnaryOperator inverse = ColorManagement.getInverseTransferFunction(this.transfer);
        DoubleUnaryOperator forward = ColorManagement.getTransferFunction(transfer);
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
