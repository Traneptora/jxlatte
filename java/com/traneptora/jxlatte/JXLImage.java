package com.traneptora.jxlatte;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

import com.traneptora.jxlatte.bundle.ImageHeader;
import com.traneptora.jxlatte.color.CIEPrimaries;
import com.traneptora.jxlatte.color.CIEXY;
import com.traneptora.jxlatte.color.ColorEncodingBundle;
import com.traneptora.jxlatte.color.ColorFlags;
import com.traneptora.jxlatte.color.ColorManagement;
import com.traneptora.jxlatte.util.Dimension;
import com.traneptora.jxlatte.util.ImageBuffer;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.functional.FloatUnaryOperator;

public class JXLImage {
    private ImageHeader imageHeader;
    private int colorEncoding;
    private int alphaIndex;

    private int primaries;
    private int whitePoint;
    private int transfer;
    private int taggedTransfer;

    private CIEXY whiteXY;
    private CIEPrimaries primariesXY;

    private byte[] iccProfile;
    private int width;
    private int height;

    private boolean alphaIsPremultiplied;

    private ImageBuffer[] buffer;
    private int[] bitDepths;

    protected JXLImage(ImageBuffer[] buffer, ImageHeader header) throws IOException {
        this.imageHeader = header;
        Dimension size = imageHeader.getOrientedSize();
        this.height = size.height;
        this.width = size.width;
        this.buffer = buffer;
        ColorEncodingBundle bundle = header.getColorEncoding();
        this.colorEncoding = bundle.colorEncoding;
        this.alphaIndex = header.hasAlpha() ? header.getAlphaIndex(0) : -1;
        this.primaries = bundle.primaries;
        this.whitePoint = bundle.whitePoint;
        this.primariesXY = bundle.prim;
        this.whiteXY = bundle.white;
        if (imageHeader.isXYBEncoded()) {
            this.transfer = ColorFlags.TF_LINEAR;
            this.iccProfile = null;
        } else {
            this.transfer = bundle.tf;
            this.iccProfile = header.getDecodedICC();
        }
        this.taggedTransfer = bundle.tf;
        this.alphaIsPremultiplied = header.hasAlpha() && imageHeader.getExtraChannelInfo(alphaIndex).alphaAssociated;
        this.bitDepths = new int[buffer.length];
        int colors = getColorChannelCount();
        for (int c = 0; c < bitDepths.length; c++) {
            if (c < colors)
                bitDepths[c] = imageHeader.getBitDepthHeader().bitsPerSample;
            else
                bitDepths[c] = imageHeader.getExtraChannelInfo(c - colors).bitDepth.bitsPerSample;
        }
    }

    private JXLImage(JXLImage image, boolean copyBuffer) {
        this.imageHeader = image.imageHeader;
        this.colorEncoding = image.colorEncoding;
        this.alphaIndex = image.alphaIndex;

        this.primaries = image.primaries;
        this.whitePoint = image.whitePoint;
        this.transfer = image.transfer;
        this.taggedTransfer = image.taggedTransfer;

        this.whiteXY = image.whiteXY;
        this.primariesXY = image.primariesXY;
        this.iccProfile = image.iccProfile;
        this.width = image.width;
        this.height = image.height;

        this.alphaIsPremultiplied = image.alphaIsPremultiplied;

        this.buffer = Stream.of(image.buffer).map(b -> new ImageBuffer(b, copyBuffer)).toArray(ImageBuffer[]::new);
        this.bitDepths = Arrays.copyOf(image.bitDepths, image.bitDepths.length);
    }

    public JXLImage(JXLImage image) {
        this(image, true);
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
        if (CIEPrimaries.matches(primariesXY, primaries) && CIEXY.matches(whiteXY, whitePoint))
            return this;
        float[][][] buffers = new float[3][][];
        for (int c = 0; c < 3; c++) {
            buffer[c].castToFloatIfInt(bitDepths[c]);
            buffers[c] = buffer[c].getFloatBuffer();
        }
        float[][] conversionMatrix =
            ColorManagement.getConversionMatrix(primaries, whitePoint, this.primariesXY, this.whiteXY);
        JXLImage image = new JXLImage(this, false);
        float[][][] ibuffers = Stream.of(image.buffer).map(ImageBuffer::getFloatBuffer).toArray(float[][][]::new);
        final float[] rgb = new float[3];
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                for (int c = 0; c < 3; c++)
                    rgb[c] = buffers[c][y][x];
                MathHelper.matrixMutliply3InPlace(conversionMatrix, rgb);
                for (int c = 0; c < 3; c++)
                    ibuffers[c][y][x] = rgb[c];
            }
        }
        image.primariesXY = primaries;
        image.whiteXY = whitePoint;
        image.primaries = ColorManagement.getPrimaries(primaries);
        image.whitePoint = ColorManagement.getWhitePoint(whitePoint);
        return image;
    }

    public JXLImage fillColor() {
        if (this.colorEncoding != ColorFlags.CE_GRAY)
            return this;
        JXLImage image = new JXLImage(this, false);
        ImageBuffer[] nbuffer = new ImageBuffer[image.buffer.length + 2];
        nbuffer[0] = new ImageBuffer(image.buffer[0].getType(), image.buffer[0].height, image.buffer[0].width);
        nbuffer[1] = new ImageBuffer(image.buffer[0].getType(), image.buffer[0].height, image.buffer[0].width);
        for (int c = 2; c < nbuffer.length; c++) {
            nbuffer[c] = image.buffer[c - 2];
        }
        image.buffer = nbuffer;
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < buffer[0].height; y++)
                System.arraycopy(buffer[0].getBackingBuffer()[y], 0, image.buffer[c].getBackingBuffer()[y], 0, buffer[0].width);
        }
        for (int c = 3; c < image.buffer.length; c++) {
            for (int y = 0; y < buffer[0].height; y++)
                System.arraycopy(buffer[c - 2].getBackingBuffer()[y], 0, image.buffer[c].getBackingBuffer()[y], 0, buffer[0].width);
        }
        image.colorEncoding = ColorFlags.CE_RGB;
        return image;
    }

    public JXLImage flattenColor() {
        if (this.colorEncoding == ColorFlags.CE_GRAY)
            return this;
        JXLImage image = new JXLImage(this, false);
        ImageBuffer[] nbuffer = new ImageBuffer[image.buffer.length - 2];
        for (int c = 2; c < image.buffer.length; c++) {
            nbuffer[c - 2] = image.buffer[c];
        }
        image.buffer = nbuffer;
        for (int y = 0; y < buffer[1].height; y++)
            System.arraycopy(buffer[1].getBackingBuffer()[y], 0, image.buffer[0].getBackingBuffer()[y], 0, buffer[1].width);
        for (int c = 1; c < image.buffer.length; c++) {
            for (int y = 0; y < buffer[0].height; y++)
                System.arraycopy(buffer[c + 2].getBackingBuffer()[y], 0, image.buffer[c].getBackingBuffer()[y], 0, buffer[1].width);
        }
        image.colorEncoding = ColorFlags.CE_GRAY;
        return image;
    }

    public JXLImage transform(CIEPrimaries primaries, CIEXY whitePoint, int transfer, int peakDetect) {
        JXLImage image = this;
        if (CIEPrimaries.matches(primaries, this.primariesXY) && CIEXY.matches(whitePoint, this.whiteXY))
            return image.transfer(transfer, peakDetect);
        return image.linearize()
            .fillColor()
            .toneMapLinear(primaries, whitePoint)
            .transfer(transfer, peakDetect);
    }

    public JXLImage transform(int primaries, int whitePoint, int transfer, int peakDetect) {
        return transform(ColorManagement.getPrimaries(primaries),
            ColorManagement.getWhitePoint(whitePoint), transfer, peakDetect);
    }

    public JXLImage toneMap(CIEPrimaries primaries, CIEXY whitePoint) {
        return transform(primaries, whitePoint, this.transfer, JXLOptions.PEAK_DETECT_OFF);
    }

    public JXLImage toneMap(int primaries, int whitePoint) {
        return transform(primaries, whitePoint, this.transfer, JXLOptions.PEAK_DETECT_OFF);
    }

    public ImageBuffer[] getBuffer(boolean copy) {
        if (!copy)
            return this.buffer;
        return Stream.of(buffer).map(b -> new ImageBuffer(b)).toArray(ImageBuffer[]::new);
    }

    private float determinePeak() {
        if (transfer != ColorFlags.TF_LINEAR)
            return linearize().determinePeak();
        int c = colorEncoding == ColorFlags.CE_GRAY ? 0 : 1;
        if (buffer[c].isInt()) {
            return Stream.of(buffer[c].getIntBuffer()).mapToInt(MathHelper::max).max().getAsInt() / (float)~(~0 << bitDepths[c]);
        } else {
            return Stream.of(buffer[c].getFloatBuffer()).map(MathHelper::max).max(Comparator.naturalOrder()).get();
        }
    }

    private JXLImage transfer(FloatUnaryOperator op) {
        int colors = getColorChannelCount();
        float[][][] buffers = new float[colors][][];
        for (int c = 0; c < colors; c++) {
            buffer[c].castToFloatIfInt(~(~0 << bitDepths[c]));
            buffers[c] = buffer[c].getFloatBuffer();
        }
        JXLImage image = new JXLImage(this, false);
        for (int c = 0; c < colors; c++) {
            float[][] b = image.buffer[c].getFloatBuffer();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    b[y][x] = op.applyAsFloat(buffers[c][y][x]);
                }
            }
        }
        return image;
    }

    private void transferInPlace(FloatUnaryOperator op) {
        int colors = getColorChannelCount();
        float[][][] buffers = new float[colors][][];
        for (int c = 0; c < colors; c++) {
            buffer[c].castToFloatIfInt(bitDepths[c]);
            buffers[c] = buffer[c].getFloatBuffer();
        }
        for (int c = 0; c < colors; c++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    buffers[c][y][x] = op.applyAsFloat(buffers[c][y][x]);
                }
            }
        }
    }

    private JXLImage linearize() {
        if (this.transfer == ColorFlags.TF_LINEAR)
            return this;
        FloatUnaryOperator toLinear = ColorManagement.getTransferFunction(this.transfer).toLinearFloatOp();
        JXLImage image = this.transfer(toLinear);
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
        image.transferInPlace(ColorManagement.getTransferFunction(transfer).fromLinearFloatOp());
        image.transfer = transfer;
        return image;
    }

    public int getTaggedBitDepth(int c) {
        return bitDepths[c];
    }

    public CIEPrimaries getCIEPrimaries() {
        return this.primariesXY;
    }

    public CIEXY getCIEWhitePoint() {
        return this.whiteXY;
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
