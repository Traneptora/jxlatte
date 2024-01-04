package com.traneptora.jxlatte;

import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferFloat;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.function.DoubleUnaryOperator;

import com.traneptora.jxlatte.bundle.ImageHeader;
import com.traneptora.jxlatte.color.CIEPrimaries;
import com.traneptora.jxlatte.color.CIEXY;
import com.traneptora.jxlatte.color.ColorEncodingBundle;
import com.traneptora.jxlatte.color.ColorFlags;
import com.traneptora.jxlatte.color.ColorManagement;
import com.traneptora.jxlatte.color.JXLColorSpace;
import com.traneptora.jxlatte.util.FlowHelper;
import com.traneptora.jxlatte.util.IntPoint;
import com.traneptora.jxlatte.util.MathHelper;

public class JXLImage {
    private FlowHelper flowHelper;

    private DataBufferFloat buffer;
    private WritableRaster raster;

    private SampleModel sampleModel;
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
    private int width;
    private int height;

    private boolean alphaIsPremultiplied;

    protected JXLImage(float[][][] buffer, ImageHeader header, FlowHelper flowHelper) throws IOException {
        this.width = buffer[0][0].length;
        this.height = buffer[0].length;
        this.flowHelper = flowHelper;
        int channels = header.getColorChannelCount() + (header.hasAlpha() ? 1 : 0);
        final float[][] dataArray  = new float[channels][width * height];
        for (int c = 0; c < channels; c++) {
            for (int y = 0; y < height; y++)
                System.arraycopy(buffer[c][y], 0, dataArray[c], y * width, width);
        }
        this.buffer = new DataBufferFloat(dataArray, width * height);
        this.sampleModel = new BandedSampleModel(DataBuffer.TYPE_FLOAT, width, height, dataArray.length);
        this.raster = Raster.createWritableRaster(this.sampleModel, this.buffer, new Point());
        ColorEncodingBundle bundle = header.getColorEncoding();
        this.colorEncoding = bundle.colorEncoding;
        this.alphaIndex = header.hasAlpha() ? header.getAlphaIndex(0) : -1;
        this.imageHeader = header;
        this.primaries = bundle.primaries;
        this.whitePoint = bundle.whitePoint;
        this.primaries1931 = bundle.prim;
        this.white1931 = bundle.white;
        if (imageHeader.isXYBEncoded()) {
            this.transfer = ColorFlags.TF_LINEAR;
            this.iccProfile = null;
        } else {
            this.transfer = bundle.tf;
            this.iccProfile = header.getDecodedICC();
        }
        this.taggedTransfer = bundle.tf;
        this.alphaIsPremultiplied = hasAlpha() && imageHeader.getExtraChannelInfo(alphaIndex).alphaAssociated;
    }

    private JXLImage(JXLImage image, boolean copyBuffer) {
        this.flowHelper = image.flowHelper;
        this.sampleModel = image.sampleModel;
        this.imageHeader = image.imageHeader;
        this.colorEncoding = image.colorEncoding;
        this.alphaIndex = image.alphaIndex;

        this.primaries = image.primaries;
        this.whitePoint = image.whitePoint;
        this.transfer = image.transfer;
        this.taggedTransfer = image.taggedTransfer;

        this.white1931 = image.white1931;
        this.primaries1931 = image.primaries1931;
        this.iccProfile = image.iccProfile;
        this.width = image.width;
        this.height = image.height;

        this.alphaIsPremultiplied = image.alphaIsPremultiplied;

        if (copyBuffer) {
            final float[][] dataBuffer = image.buffer.getBankData();
            final float[][] buf = new float[dataBuffer.length][width * height];
            for (int c = 0; c < buf.length; c++)
                System.arraycopy(dataBuffer[c], 0, buf[c], 0, dataBuffer[c].length);
            this.buffer = new DataBufferFloat(buf, buf[0].length);
            this.raster = Raster.createWritableRaster(this.sampleModel, this.buffer, new Point());
        }
    }

    public JXLImage(JXLImage image) {
        this(image, true);
    }

    private ColorModel getColorModel() {
        boolean alpha = this.hasAlpha();
        int transparency = alpha ? Transparency.TRANSLUCENT : Transparency.OPAQUE;
        ColorSpace cs;
        if (this.hasICCProfile())
            cs = new ICC_ColorSpace(ICC_Profile.getInstance(iccProfile));
        else
            cs = new JXLColorSpace(this.primaries1931, this.white1931, this.transfer,
                imageHeader.getColorChannelCount());
        return new ComponentColorModel(cs, alpha, alphaIsPremultiplied, transparency, DataBuffer.TYPE_FLOAT);
    }

    public BufferedImage asBufferedImage() {
        return new BufferedImage(getColorModel(), raster, alphaIsPremultiplied, null);
    }

    public boolean isHDR() {
        switch(taggedTransfer) {
            case ColorFlags.TF_PQ:
            case ColorFlags.TF_HLG:
            case ColorFlags.TF_LINEAR:
                return true;
        }
        ColorEncodingBundle color = imageHeader.getColorEncoding();
        return !CIEPrimaries.matches(color.prim, ColorManagement.PRI_SRGB)
            && !CIEPrimaries.matches(color.prim, ColorManagement.PRI_P3);
    }

    /*
     * Assumes Linear Light
     */
    private JXLImage toneMapLinear(CIEPrimaries primaries, CIEXY whitePoint) {
        if (CIEPrimaries.matches(primaries1931, primaries) && CIEXY.matches(white1931, whitePoint))
            return this;
        float[][] conversionMatrix =
            ColorManagement.getConversionMatrix(primaries, whitePoint, this.primaries1931, this.white1931);
        JXLImage image = new JXLImage(this, false);
        image.buffer = new DataBufferFloat(buffer.getSize(), buffer.getNumBanks());
        image.raster = Raster.createWritableRaster(image.sampleModel, image.buffer, new Point());
        final float[] rgb = raster.getPixel(0, 0, (float[])null);
        for (IntPoint p : FlowHelper.range2D(width, height)) {
            raster.getPixel(p.x, p.y, rgb);
            image.raster.setPixel(p.x, p.y, MathHelper.matrixMutliply(conversionMatrix, rgb));
        }
        image.primaries1931 = primaries;
        image.white1931 = whitePoint;
        image.primaries = ColorFlags.getPrimaries(primaries);
        image.whitePoint = ColorFlags.getWhitePoint(whitePoint);
        return image;
    }

    public JXLImage fillColor() {
        if (this.colorEncoding != ColorFlags.CE_GRAY)
            return this;
        final float[][] dataArray = new float[buffer.getNumBanks() + 2][buffer.getSize()];
        final float[][] src = buffer.getBankData();
        for (int c = 0; c < dataArray.length; c++) {
            final int cIn = c > 2 ? c - 2 : 0;
            System.arraycopy(src[cIn], 0, dataArray[c], 0, buffer.getSize());
        }
        JXLImage image = new JXLImage(this, false);
        image.buffer = new DataBufferFloat(dataArray, buffer.getSize());
        image.sampleModel = new BandedSampleModel(DataBuffer.TYPE_FLOAT, image.width, image.height, dataArray.length);
        image.raster = Raster.createWritableRaster(image.sampleModel, image.buffer, new Point());
        image.colorEncoding = ColorFlags.CE_RGB;
        return image;
    }

    public JXLImage flattenColor() {
        if (this.colorEncoding == ColorFlags.CE_GRAY)
            return this;
        final float[][] dataArray = new float[buffer.getNumBanks() - 2][buffer.getSize()];
        final float[][] src = buffer.getBankData();
        for (int c = 0; c < dataArray.length; c++) {
            final int cIn = c > 1 ? c + 2 : 1;
            System.arraycopy(src[cIn], 0, dataArray[c], 0, buffer.getSize());
        }
        JXLImage image = new JXLImage(this, false);
        image.buffer = new DataBufferFloat(dataArray, buffer.getSize());
        image.sampleModel = new BandedSampleModel(DataBuffer.TYPE_FLOAT, image.width, image.height, dataArray.length);
        image.raster = Raster.createWritableRaster(image.sampleModel, image.buffer, new Point());
        image.colorEncoding = ColorFlags.CE_GRAY;
        return image;
    }

    public JXLImage transform(CIEPrimaries primaries, CIEXY whitePoint, int transfer, int peakDetect) {
        JXLImage image = this;
        if (CIEPrimaries.matches(primaries, this.primaries1931) && CIEXY.matches(whitePoint, this.white1931))
            return image.transfer(transfer, peakDetect);
        return image.linearize()
            .fillColor()
            .toneMapLinear(primaries, whitePoint)
            .transfer(transfer, peakDetect);
    }

    public JXLImage transform(int primaries, int whitePoint, int transfer, int peakDetect) {
        return transform(ColorFlags.getPrimaries(primaries), ColorFlags.getWhitePoint(whitePoint),
            transfer, peakDetect);
    }

    public JXLImage toneMap(CIEPrimaries primaries, CIEXY whitePoint) {
        return transform(primaries, whitePoint, this.transfer, JXLOptions.PEAK_DETECT_OFF);
    }

    public JXLImage toneMap(int primaries, int whitePoint) {
        return transform(primaries, whitePoint, this.transfer, JXLOptions.PEAK_DETECT_OFF);
    }

    private float determinePeak() {
        float max = MathHelper.max(
            transform(null, null, ColorFlags.TF_LINEAR, JXLOptions.PEAK_DETECT_OFF)
            .buffer.getData(1));
        return max;
    }

    private JXLImage transfer(DoubleUnaryOperator op) {
        JXLImage image = new JXLImage(this);
        for (int c = 0; c < imageHeader.getColorChannelCount(); c++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                     image.raster.setSample(x, y, c, op.applyAsDouble(this.raster.getSampleFloat(x, y, c)));
                }
            }
        }
        return image;
    }

    private JXLImage linearize() {
        if (this.transfer == ColorFlags.TF_LINEAR)
            return this;

        DoubleUnaryOperator inverse =  ColorManagement.getInverseTransferFunction(this.transfer);
        JXLImage image = this.transfer(inverse);
        image.transfer = ColorFlags.TF_LINEAR;
        return image;
    }

    public JXLImage transfer(int transfer, int peakDetect) {
        if (transfer == this.transfer)
            return this;
        JXLImage image = this.linearize();
        if (taggedTransfer == ColorFlags.TF_PQ &&
                (peakDetect == JXLOptions.PEAK_DETECT_AUTO || peakDetect == JXLOptions.PEAK_DETECT_ON)) {
            boolean toPQ = transfer == ColorFlags.TF_PQ || transfer == ColorFlags.TF_LINEAR;
            boolean fromPQ = this.transfer == ColorFlags.TF_PQ || this.transfer == ColorFlags.TF_LINEAR;
            if (fromPQ && !toPQ) {
                final float scale = 1.0f / image.determinePeak();
                if (scale > 1.0f || peakDetect == JXLOptions.PEAK_DETECT_ON)
                    image = image.transfer(f -> f * scale);
            }
        }
        DoubleUnaryOperator forward = ColorManagement.getTransferFunction(transfer);
        image = image.transfer(forward);
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
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean hasICCProfile() {
        return iccProfile != null;
    }

    public byte[] getICCProfile() {
        return iccProfile;
    }

    public int getColorChannelCount() {
        return this.colorEncoding == ColorFlags.CE_GRAY ? 1 : 3;
    }

    public boolean isAlphaPremultiplied() {
        return alphaIsPremultiplied;
    }
}
