package com.thebombzen.jxlatte.io;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

import com.thebombzen.jxlatte.MathHelper;

public class PNGWriter {
    private int bitDepth;
    private int[][][] buffer;
    private DataOutputStream out;
    private int maxValue;
    private int inputMaxValue;
    private int width;
    private int height;
    private int colorMode;
    private boolean grayScale;
    private int alphaIndex;
    private CRC32 crc32 = new CRC32();

    public PNGWriter(int inputBitDepth, int bitDepth, int[][][] buffer, boolean grayScale, int alphaIndex) {
        if (bitDepth != 8 && bitDepth != 16)
            throw new IllegalArgumentException();
        this.bitDepth = bitDepth;
        this.buffer = buffer;
        this.maxValue = ~(~0 << bitDepth);
        this.inputMaxValue = ~(~0 << inputBitDepth);
        this.width = buffer[0][0].length;
        this.height = buffer[0].length;
        this.grayScale = grayScale;
        this.alphaIndex = alphaIndex;
        if (grayScale)
            this.colorMode = alphaIndex >= 0 ? 4 : 0;
        else
            this.colorMode = alphaIndex >= 0 ? 6 : 2;
    }

    private void writeIHDR() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        dout.writeInt(0x49_48_44_52); // IHDR
        dout.writeInt(width);
        dout.writeInt(height);
        dout.writeByte(bitDepth);
        dout.writeByte(colorMode);
        dout.writeByte(0); // compression method
        dout.writeByte(0); // filter method
        dout.writeByte(0); // not interlaced
        dout.close();
        byte[] buf = bout.toByteArray();
        crc32.reset();
        out.writeInt(buf.length - 4);
        out.write(buf);
        crc32.update(buf);
        out.writeInt((int)crc32.getValue());
    }

    private void writeSample(DataOutput dout, int x, int y, int c) throws IOException {
        int s = (int)((long)buffer[c][y][x] * maxValue / inputMaxValue);
        s = MathHelper.clamp(s, 0, maxValue);
        if (bitDepth == 8)
            dout.writeByte(s);
        else
            dout.writeShort(s);
    }

    public void write(OutputStream outputStream) throws IOException {
        this.out = new DataOutputStream(outputStream);
        out.writeLong(0x89504E470D0A1A0AL);
        writeIHDR();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        bout.write(new byte[]{'I', 'D', 'A', 'T'});
        DataOutputStream dout = new DataOutputStream(new DeflaterOutputStream(bout));
        for (int y = 0; y < height; y++) {
            dout.writeByte(0); // filter
            for (int x = 0; x < width; x++) {
                int channels = grayScale ? 1 : 3;
                for (int c = 0; c < channels; c++) {
                    writeSample(dout, x, y, c);
                }
                if (alphaIndex >= 0)
                    writeSample(dout, x, y, alphaIndex);
            }
        }
        dout.close();
        byte[] buff = bout.toByteArray();
        out.writeInt(buff.length - 4);
        out.write(buff);
        crc32.reset();
        crc32.update(buff);
        out.writeInt((int)crc32.getValue());
        out.writeInt(0);
        out.writeInt(0x49_45_4E_44); // IEND
        out.writeInt(0xAE_42_60_82); // crc32
    }
}
