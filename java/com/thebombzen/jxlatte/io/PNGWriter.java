package com.thebombzen.jxlatte.io;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import com.thebombzen.jxlatte.JXLImage;
import com.thebombzen.jxlatte.color.CIEPrimaries;
import com.thebombzen.jxlatte.color.CIEXY;
import com.thebombzen.jxlatte.color.ColorFlags;
import com.thebombzen.jxlatte.util.MathHelper;

public class PNGWriter {
    private int bitDepth;
    private double[][][] buffer;
    private DataOutputStream out;
    private int maxValue;
    private int width;
    private int height;
    private int colorMode;
    private int colorChannels;
    private int alphaIndex;
    private int deflateLevel;
    private CIEPrimaries primaries;
    private CIEXY whitePoint;
    private CRC32 crc32 = new CRC32();

    public PNGWriter(JXLImage image) {
        this(image, 8);
    }

    public PNGWriter(JXLImage image, int bitDepth) {
        this(image, bitDepth, Deflater.BEST_SPEED);
    }

    public PNGWriter(JXLImage image, int bitDepth, int deflateLevel) {
        this(image, bitDepth, deflateLevel, null, null);
    }

    public PNGWriter(JXLImage image, int bitDepth, int deflateLevel, CIEPrimaries primaries, CIEXY whitePoint) {
        if (bitDepth != 8 && bitDepth != 16)
            throw new IllegalArgumentException("PNG only supports 8 and 16");
        boolean gray = image.getColorEncoding() == ColorFlags.CE_GRAY;
        this.primaries = primaries;
        this.whitePoint = whitePoint;
        if (primaries == null)
            primaries = ColorFlags.getPrimaries(ColorFlags.PRI_SRGB);
        if (whitePoint == null)
            whitePoint = ColorFlags.getWhitePoint(ColorFlags.WP_D65);
        image = image.transform(primaries, whitePoint, ColorFlags.TF_SRGB);
        this.buffer = image.getBuffer();
        this.bitDepth = bitDepth;
        this.maxValue = ~(~0 << bitDepth);
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.alphaIndex = image.getAlphaIndex();
        this.deflateLevel = deflateLevel;
        if (gray) {
            this.colorMode = alphaIndex >= 0 ? 4 : 0;
            this.colorChannels = 1;
        } else {
            this.colorMode = alphaIndex >= 0 ? 6 : 2;
            this.colorChannels = 3;
        }
    }

    private void writeIHDR() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        dout.writeInt(0x49_48_44_52); // IHDR
        dout.writeInt(width);
        dout.writeInt(height);
        dout.writeByte(bitDepth);
        dout.writeByte(colorMode);
        dout.writeByte(0); // compression method 0 (zlib)
        dout.writeByte(0); // filter method 0 (standard)
        dout.writeByte(0); // not interlaced
        dout.close();
        byte[] buf = bout.toByteArray();
        crc32.reset();
        out.writeInt(buf.length - 4);
        out.write(buf);
        crc32.update(buf);
        out.writeInt((int)crc32.getValue());
    }

    private void writeCHRM() throws IOException {
        if (primaries == null && whitePoint == null)
            return;
        if (primaries == null)
            primaries = ColorFlags.getPrimaries(ColorFlags.PRI_SRGB);
        if (whitePoint == null)
            whitePoint = ColorFlags.getWhitePoint(ColorFlags.WP_D65);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        dout.writeInt(0x63_48_52_4D);
        dout.writeInt((int)(100000D * whitePoint.x));
        dout.writeInt((int)(100000D * whitePoint.y));
        dout.writeInt((int)(100000D * primaries.red.x));
        dout.writeInt((int)(100000D * primaries.red.y));
        dout.writeInt((int)(100000D * primaries.green.x));
        dout.writeInt((int)(100000D * primaries.green.y));
        dout.writeInt((int)(100000D * primaries.blue.x));
        dout.writeInt((int)(100000D * primaries.blue.y));
        dout.close();
        byte[] buf = bout.toByteArray();
        crc32.reset();
        out.writeInt(buf.length - 4);
        out.write(buf);
        crc32.update(buf);
        out.writeInt((int)crc32.getValue());
    }

    private void writeSample(DataOutput dout, int x, int y, int c) throws IOException {
        int s = MathHelper.round(buffer[c][y][x] * maxValue);
        s = MathHelper.clamp(s, 0, maxValue);
        if (bitDepth == 8)
            dout.writeByte(s);
        else
            dout.writeShort(s);
    }

    private void writeIDAT() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        bout.write(new byte[]{'I', 'D', 'A', 'T'});
        DataOutputStream dout = new DataOutputStream(new DeflaterOutputStream(bout, new Deflater(deflateLevel)));
        for (int y = 0; y < height; y++) {
            dout.writeByte(0); // filter 0
            for (int x = 0; x < width; x++) {
                for (int c = 0; c < colorChannels; c++) {
                    writeSample(dout, x, y, c);
                }
                if (alphaIndex >= 0)
                    writeSample(dout, x, y, colorChannels + alphaIndex);
            }
        }
        dout.close();
        byte[] buff = bout.toByteArray();
        out.writeInt(buff.length - 4);
        out.write(buff);
        crc32.reset();
        crc32.update(buff);
        out.writeInt((int)crc32.getValue());
    }

    public void write(OutputStream outputStream) throws IOException {
        this.out = new DataOutputStream(outputStream);
        out.writeLong(0x8950_4E47_0D0A_1A0AL); // png signature
        writeIHDR();
        writeCHRM();
        writeIDAT();
        out.writeInt(0);
        out.writeInt(0x49_45_4E_44); // IEND
        out.writeInt(0xAE_42_60_82); // crc32 for IEND
    }
}
