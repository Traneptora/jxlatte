package com.thebombzen.jxlatte.io;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import com.thebombzen.jxlatte.JXLImage;
import com.thebombzen.jxlatte.JXLOptions;
import com.thebombzen.jxlatte.JXLatte;
import com.thebombzen.jxlatte.color.CIEPrimaries;
import com.thebombzen.jxlatte.color.CIEXY;
import com.thebombzen.jxlatte.color.ColorFlags;
import com.thebombzen.jxlatte.color.ColorManagement;
import com.thebombzen.jxlatte.util.MathHelper;

public class PNGWriter {
    private int bitDepth;
    private WritableRaster raster;
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
    private byte[] iccProfile = null;
    private boolean hdr;
    private int tf;

    public PNGWriter(JXLImage image) {
        this(image, -1, false, JXLOptions.PEAK_DETECT_AUTO);
    }

    public PNGWriter(JXLImage image, boolean hdr) {
        this(image, -1, hdr, JXLOptions.PEAK_DETECT_AUTO);
    }

    public PNGWriter(JXLImage image, int bitDepth, boolean hdr, int peakDetect) {
        this(image, bitDepth, Deflater.DEFAULT_COMPRESSION, hdr, peakDetect);
    }

    public PNGWriter(JXLImage image, int bitDepth, int deflateLevel, boolean hdr, int peakDetect) {
        if (bitDepth <= 0)
            bitDepth = hdr || image.getHeader().getBitDepthHeader().bitsPerSample > 8 ? 16 : 8;
        if (bitDepth != 8 && bitDepth != 16)
            throw new IllegalArgumentException("PNG only supports 8 and 16");
        this.hdr = hdr;
        boolean gray = image.getColorEncoding() == ColorFlags.CE_GRAY;
        this.primaries = hdr ? ColorManagement.PRI_BT2100 : ColorManagement.PRI_SRGB;
        this.whitePoint = ColorManagement.WP_D65;
        this.tf = hdr ? ColorFlags.TF_PQ : ColorFlags.TF_SRGB;
        this.iccProfile = image.getICCProfile();
        image = iccProfile != null ? image : image.transform(primaries, whitePoint, tf, peakDetect);
        BufferedImage bufferedImage = image.asBufferedImage();
        this.raster = bufferedImage.getRaster();
        bufferedImage.getColorModel().coerceData(this.raster, false);
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

    private void writeSRGB() throws IOException {
        if (iccProfile != null || hdr)
            return;
        DataOutputStream dout = new DataOutputStream(out);
        dout.writeInt(0x00_00_00_01);
        dout.writeInt(0x73_52_47_42); // sRGB
        dout.write(1); // relative colorimetric
        dout.writeInt(0xD9_C9_2C_7F); // crc
        dout.flush();
    }

    private void writeICCP() throws IOException {
        if (iccProfile == null) {
            if (hdr) {
                this.iccProfile = new byte[8708];
                try (InputStream in = JXLatte.class.getResourceAsStream("/bt2020-d65-pq.icc")) {
                    IOHelper.readFully(in, this.iccProfile);
                }
            } else {
                return;
            }
        }
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        dout.writeInt(0x69_43_43_50); // iCCP
        byte[] s = "jxlatte".getBytes(StandardCharsets.UTF_8);
        dout.write(s);
        dout.write(0); // null terminator
        dout.write(0); // compression method 0
        DeflaterOutputStream defout = new DeflaterOutputStream(dout, new Deflater(deflateLevel));
        defout.write(iccProfile);
        defout.flush();
        defout.close();
        byte[] buf = bout.toByteArray();
        out.writeInt(buf.length - 4);
        out.write(buf);
        crc32.reset();
        crc32.update(buf);
        out.writeInt((int)crc32.getValue());
    }

    private void writeSample(DataOutput dout, int x, int y, int c) throws IOException {
        int s = MathHelper.round(raster.getSampleFloat(x, y, c) * maxValue);
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
                for (int c = 0; c < colorChannels; c++)
                    writeSample(dout, x, y, c);
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
        if (hdr || this.iccProfile != null)
            writeICCP();
        else
            writeSRGB();
        writeIDAT();
        out.writeInt(0);
        out.writeInt(0x49_45_4E_44); // IEND
        out.writeInt(0xAE_42_60_82); // crc32 for IEND
    }
}
