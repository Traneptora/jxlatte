package com.thebombzen.jxlatte;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class Bitreader implements Closeable {
    private InputStream in;
    private long cache = 0;
    private int cache_bits = 0;

    public Bitreader(InputStream in) {
        this.in = in;
    }

    public int readBits(int bits) throws IOException {
        if (bits == 0)
            return 0;
        if (bits < 0 || bits > 32)
            throw new IllegalArgumentException("Must read between 0-32 bits, inclusive");
        if (bits <= cache_bits) {
            int ret = (int)(cache & ~(~0L << bits));
            cache_bits -= bits;
            cache >>>= bits;
            return ret;
        }
        int count = in.available();
        int max = (63 - cache_bits) / 8 + 1;
        count = count > 0 ? (count < max ? count : max) : 1;
        boolean eof = false;
        for (int i = 0; i < count; i++) {
            int b = in.read();
            if (b < 0) {
                eof = true;
                break;
            }
            cache = cache | ((b & 0xFFL) << cache_bits);
            cache_bits += 8;
        }
        if (eof && bits > cache_bits)
            throw new EOFException("Unable to read enough bits");
        return readBits(bits);
    }

    public boolean readBool() throws IOException {
        return readBits(1) != 0;
    }

    public int readU32(int c0, int u0, int c1, int u1, int c2, int u2, int c3, int u3) throws IOException {
        int choice = readBits(2);
        int c, u;
        switch (choice) {
            case 1:
                c = c1; u = u1;
                break;
            case 2:
                c = c1; u = u1;
                break;
            case 3:
                c = c1; u = u1;
                break;
            default:
                c = c0; u = u0;
        }

        return c + readBits(u);
    }

    public long readU64() throws IOException {
        long value = readBits(12);
        int shift = 12;
        while (readBool()) {
            if (shift == 60) {
                value |= (long)readBits(4) << shift;
                break;
            }
            value |= (long)readBits(8) << shift;
            shift += 8;
        }
        return value;
    }

    public float readF16() throws IOException, InvalidBitstreamException {
        int bits16 = readBits(16);
        int mantissa = (bits16 & 0x3FF) << 13;
        int biased_exp = ((bits16 >> 10) & 0x1F) + 112;
        // 143 == 31 + 127 - 15 == 31 + 112
        if (biased_exp == 143)
            throw new InvalidBitstreamException("Illegal infinite/NaN float16");
        int sign = (bits16 & 0x8000) << 16;
        int total = sign | (biased_exp << 23) | mantissa;
        return Float.intBitsToFloat(total);
    }

    public int readEnum() throws IOException, InvalidBitstreamException {
        int constant = readU32(0, 0, 1, 0, 2, 4, 18, 6);
        if (constant > 63)
            throw new InvalidBitstreamException("Enum constant > 63");
        return constant;
    }

    public void zeroPadToByte() throws IOException, InvalidBitstreamException {
        int remaining = 8 - cache_bits % 8;
        if (remaining < 8) {
            int padding = readBits(remaining);
            if (padding != 0)
                throw new InvalidBitstreamException("Nonzero zero-padding-to-byte");
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
