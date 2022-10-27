package com.thebombzen.jxlatte.io;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

public class PNGWriter {
    private int bitDepth;
    private int[][][] buffer;
    private DataOutputStream out;
    private int maxValue;
    private int inputMaxValue;
    private int width;
    private int height;
    private CRC32 crc32 = new CRC32();
    private static final byte[] IEND = new byte[]{'I', 'E', 'N', 'D'};
    private static final byte[] IHDR = new byte[]{'I', 'H', 'D', 'R'};

    public PNGWriter(int inputBitDepth, int bitDepth, int[][][] buffer) {
        if (bitDepth != 8 && bitDepth != 16)
            throw new IllegalArgumentException();
        this.bitDepth = bitDepth;
        this.buffer = buffer;
        this.maxValue = ~(~0 << bitDepth);
        this.inputMaxValue = ~(~0 << inputBitDepth);
        this.width = buffer[0][0].length;
        this.height = buffer[0].length;
    }

    private void writeIHDR() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        dout.write(IHDR);
        dout.writeInt(width);
        dout.writeInt(height);
        dout.writeByte(bitDepth);
        dout.writeByte(2); // color type == truecolor
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

    public void write(OutputStream outputStream) throws IOException {
        this.out = new DataOutputStream(outputStream);
        out.write(new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10});
        writeIHDR();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        bout.write(new byte[]{'I', 'D', 'A', 'T'});
        DataOutputStream dout = new DataOutputStream(new DeflaterOutputStream(bout));
        for (int y = 0; y < height; y++) {
            dout.writeByte(0); // filter
            for (int x = 0; x < width; x++) {
                for (int c = 0; c < 3; c++) {
                    int s = buffer[c][y][x] * maxValue / inputMaxValue;
                    if (bitDepth == 8)
                        dout.writeByte(s);
                    else
                        dout.writeShort(s);
                }
            }
        }
        dout.close();
        byte[] buff = bout.toByteArray();
        out.writeInt(buff.length - 4);
        out.write(buff);
        crc32.reset();
        crc32.update(buff);
        out.writeInt((int)crc32.getValue());
        crc32.reset();
        crc32.update(IEND);
        out.writeInt(0);
        out.write(IEND);
        out.writeInt((int)crc32.getValue());
    }
}
