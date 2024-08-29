package com.traneptora.jxlatte;

import java.io.IOException;

import com.traneptora.jxlatte.bundle.ImageHeader;
import com.traneptora.jxlatte.color.CIEPrimaries;
import com.traneptora.jxlatte.color.CIEXY;
import com.traneptora.jxlatte.color.ColorEncodingBundle;
import com.traneptora.jxlatte.color.ColorFlags;
import com.traneptora.jxlatte.color.ColorManagement;
import com.traneptora.jxlatte.util.FlowHelper;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.functional.FloatUnaryOperator;

public class JXLImage {
    private FlowHelper flowHelper;

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

    private float[][] buffer;

    protected JXLImage(float[][][] buffer, ImageHeader header, FlowHelper flowHelper) throws IOException {
        this.width = buffer[0][0].length;
        this.height = buffer[0].length;
        this.flowHelper = flowHelper;
        int channels = header.getTotalChannelCount();
        this.buffer = new float[channels][width * height];
        for (int c = 0; c < channels; c++) {
            for (int y = 0; y < height; y++)
                System.arraycopy(buffer[c][y], 0, this.buffer[c], y * width, width);
        }
        ColorEncodingBundle bundle = header.getColorEncoding();
        this.colorEncoding = bundle.colorEncoding;
        this.alphaIndex = header.hasAlpha() ? header.getAlphaIndex(0) : -1;
        this.imageHeader = header;
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
    }

    private JXLImage(JXLImage image, boolean copyBuffer) {
        this.flowHelper = image.flowHelper;
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

        if (copyBuffer)
            this.buffer = image.getBuffer(true);
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
        float[][] conversionMatrix =
            ColorManagement.getConversionMatrix(primaries, whitePoint, this.primariesXY, this.whiteXY);
        JXLImage image = new JXLImage(this, false);
        image.buffer = new float[buffer.length][buffer[0].length];
        final float[] rgb = new float[3];
        for (int p = 0; p < buffer[0].length; p++) {
            for (int c = 0; c < 3; c++)
                rgb[c] = buffer[c][p];
            MathHelper.matrixMutliply3InPlace(conversionMatrix, rgb);
            for (int c = 0; c < 3; c++)
                image.buffer[c][p] = rgb[c];
        }
        image.primariesXY = primaries;
        image.whiteXY = whitePoint;
        image.primaries = ColorFlags.getPrimaries(primaries);
        image.whitePoint = ColorFlags.getWhitePoint(whitePoint);
        return image;
    }

    public JXLImage fillColor() {
        if (this.colorEncoding != ColorFlags.CE_GRAY)
            return this;
        JXLImage image = new JXLImage(this, false);
        image.buffer = new float[this.buffer.length + 2][this.buffer[0].length];
        for (int c = 0; c < 3; c++)
            System.arraycopy(buffer[0], 0, image.buffer[c], 0, buffer[0].length);
        for (int c = 3; c < image.buffer.length; c++)
            System.arraycopy(buffer[c - 2], 0, image.buffer[c], 0, buffer[c - 2].length);
        image.colorEncoding = ColorFlags.CE_RGB;
        return image;
    }

    public JXLImage flattenColor() {
        if (this.colorEncoding == ColorFlags.CE_GRAY)
            return this;
        JXLImage image = new JXLImage(this, false);
        image.buffer = new float[buffer.length - 2][buffer[0].length];
        System.arraycopy(buffer[1], 0, image.buffer[0], 0, buffer[1].length);
        for (int c = 1; c < image.buffer.length; c++)
            System.arraycopy(buffer[c + 2], 0, image.buffer[c], 0, buffer[c + 2].length);
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
        return transform(ColorFlags.getPrimaries(primaries), ColorFlags.getWhitePoint(whitePoint),
            transfer, peakDetect);
    }

    public JXLImage toneMap(CIEPrimaries primaries, CIEXY whitePoint) {
        return transform(primaries, whitePoint, this.transfer, JXLOptions.PEAK_DETECT_OFF);
    }

    public JXLImage toneMap(int primaries, int whitePoint) {
        return transform(primaries, whitePoint, this.transfer, JXLOptions.PEAK_DETECT_OFF);
    }

    public float[][] getBuffer(boolean copy) {
        if (!copy)
            return this.buffer;
        final float[][] buf = new float[buffer.length][buffer[0].length];
        for (int c = 0; c < buf.length; c++)
            System.arraycopy(buffer[c], 0, buf[c], 0, buffer[c].length);
        return buf;
    }

    private float determinePeak() {
        if (transfer != ColorFlags.TF_LINEAR)
            return linearize().determinePeak();
        int c = colorEncoding == ColorFlags.CE_GRAY ? 0 : 1;
        return MathHelper.max(buffer[c]);
    }

    private JXLImage transfer(FloatUnaryOperator op) {
        JXLImage image = new JXLImage(this, false);
        for (int c = 0; c < imageHeader.getColorChannelCount(); c++) {
            for (int p = 0; p < image.buffer[0].length; p++) {
                image.buffer[c][p] = op.applyAsFloat(buffer[c][p]);
            }
        }
        return image;
    }

    private void transferInPlace(FloatUnaryOperator op) {
        for (int c = 0; c < imageHeader.getColorChannelCount(); c++) {
            for (int p = 0; p < buffer[0].length; p++) {
                buffer[c][p] = op.applyAsFloat(buffer[c][p]);
            }
        }
    }

    private JXLImage linearize() {
        if (this.transfer == ColorFlags.TF_LINEAR)
            return this;

        FloatUnaryOperator toLinear = ColorManagement.getTransferFunction(this.transfer).toLinearFloatOp();
        JXLImage image = transfer(toLinear);
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
