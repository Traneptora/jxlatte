package com.thebombzen.jxlatte;

import java.io.IOException;
import java.util.function.DoubleUnaryOperator;

import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.color.CIEPrimaries;
import com.thebombzen.jxlatte.color.CIEXY;
import com.thebombzen.jxlatte.color.ColorEncodingBundle;
import com.thebombzen.jxlatte.color.ColorFlags;
import com.thebombzen.jxlatte.color.ColorManagement;
import com.thebombzen.jxlatte.util.FlowHelper;
import com.thebombzen.jxlatte.util.IntPoint;
import com.thebombzen.jxlatte.util.MathHelper;

public class JXLImage {
    private double[][][] buffer;
    private ImageHeader imageHeader;
    private int colorEncoding;
    private int alphaIndex;

    private int primaries;
    private int whitePoint;
    private int transfer;
    private int taggedTransfer;

    private CIEXY white1931;
    private CIEPrimaries primaries1931;
    private byte[] iccProfile;

    protected JXLImage(double[][][] buffer, ImageHeader header) throws IOException {
        this.buffer = buffer;
        this.colorEncoding = header.getColorEncoding().colorEncoding;
        this.alphaIndex = header.hasAlpha() ? header.getAlphaIndex(0) : -1;
        this.imageHeader = header;
        ColorEncodingBundle bundle = header.getColorEncoding();
        if (imageHeader.isXYBEncoded()) {
            this.transfer = ColorFlags.TF_LINEAR;
            this.iccProfile = null;
        } else {
            this.transfer = bundle.tf;
            this.iccProfile = header.getDecodedICC();
        }
        this.taggedTransfer = bundle.tf;
        this.primaries = bundle.primaries;
        this.whitePoint = bundle.whitePoint;
        this.primaries1931 = bundle.prim;
        this.white1931 = bundle.white;
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
        this.iccProfile = image.iccProfile;
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

    /*
     * Assumes Linear Light
     */
    private JXLImage toneMapLinear(CIEPrimaries primaries, CIEXY whitePoint) {
        if (this.primaries1931.matches(primaries) && this.white1931.matches(whitePoint))
            return this;
        double[][] conversionMatrix = ColorManagement.getConversionMatrix(primaries, whitePoint, this.primaries1931, this.white1931);
        int width = getWidth();
        int height = getHeight();
        JXLImage image = new JXLImage(this);
        FlowHelper.parallelIterate(new IntPoint(width, height), (x, y) -> {
            double[] rgb = new double[]{buffer[0][y][x], buffer[1][y][x], buffer[2][y][x]};
            double[] rgb2 = MathHelper.matrixMutliply(conversionMatrix, rgb);
            image.buffer[0][y][x] = rgb2[0];
            image.buffer[1][y][x] = rgb2[1];
            image.buffer[2][y][x] = rgb2[2];
        });
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
        for (int c = 0; c < newBuffer.length; c++) {
            for (int y = 0; y < h; y++) {
                System.arraycopy(buffer[c > 2 ? c - 2 : 0][y], 0, newBuffer[c][y], 0, w);
            }
        }
        JXLImage image = new JXLImage(this, false);
        image.buffer = newBuffer;
        image.colorEncoding = ColorFlags.CE_RGB;
        return image;
    }

    public JXLImage flattenColor() {
        if (this.colorEncoding == ColorFlags.CE_GRAY)
            return this;
        int w = getWidth();
        int h = getHeight();
        double[][][] newBuffer = new double[buffer.length - 2][h][w];
        for (int c = 0; c < newBuffer.length; c++) {
            for (int y = 0; y < h; y++) {
                System.arraycopy(buffer[c > 1 ? c + 2 : 1][y], 0, newBuffer[c][y], 0, w);
            }
        }
        JXLImage image = new JXLImage(this, false);
        image.buffer = newBuffer;
        image.colorEncoding = ColorFlags.CE_GRAY;
        return image;
    }

    public JXLImage invertXYB() {
        return invertXYB(this.primaries1931, this.white1931);
    }

    public JXLImage invertXYB(CIEPrimaries primaries, CIEXY whitePoint) {
        if (this.colorEncoding != ColorFlags.CE_XYB)
            return this;
        JXLImage image = new JXLImage(this);
        image.primaries1931 = primaries;
        image.primaries = ColorFlags.getPrimaries(primaries);
        image.white1931 = whitePoint;
        image.whitePoint = ColorFlags.getWhitePoint(whitePoint);
        imageHeader.getOpsinInverseMatrix().getMatrix(primaries, whitePoint)
            .invertXYB(image.buffer, imageHeader.getToneMapping().intensityTarget);
        image.colorEncoding = ColorFlags.CE_RGB;
        return image;
    }

    public JXLImage transform(CIEPrimaries primaries, CIEXY whitePoint, int transfer) {
        JXLImage image = this;
        if (image.colorEncoding == ColorFlags.CE_XYB)
            image = image.invertXYB(primaries, whitePoint);
        if (image.primaries1931.matches(primaries) && image.white1931.matches(whitePoint))
            return image.transfer(transfer);
        return image.transfer(ColorFlags.TF_LINEAR)
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
        JXLImage image = this;
        if (image.colorEncoding == ColorFlags.CE_XYB)
            image = image.invertXYB();
        if (transfer == image.transfer)
            return image;
        image = image == this ? new JXLImage(image) : image;
        DoubleUnaryOperator inverse = ColorManagement.getInverseTransferFunction(image.transfer);
        DoubleUnaryOperator forward = ColorManagement.getTransferFunction(transfer);
        DoubleUnaryOperator composed = inverse.andThen(forward);
        int width = getWidth();
        int height = getHeight();
        double[][][] imBuffer = image.buffer;
        FlowHelper.parallelIterate(buffer.length, new IntPoint(width, height), (c, x, y) -> {
            imBuffer[c][y][x] = composed.applyAsDouble(buffer[c][y][x]);
        });
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

    public int getTaggedTransfer() {
        return this.taggedTransfer;
    }

    public ImageHeader getHeader() {
        return imageHeader;
    }

    public int getWidth() {
        return imageHeader.getOrientedWidth();
    }

    public int getHeight() {
        return imageHeader.getOrientedHeight();
    }

    public double[][][] getBuffer() {
        return buffer;
    }

    public boolean hasICCProfile() {
        return iccProfile != null;
    }

    public byte[] getICCProfile() {
        return iccProfile;
    }
}
