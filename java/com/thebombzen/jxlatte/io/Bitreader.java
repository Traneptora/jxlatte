package com.thebombzen.jxlatte.io;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;

public interface Bitreader extends Closeable {

    /**
     * @param bits Reads 0-32 bits from the bitstream
     * @return the value of those bits
     */
    public int readBits(int bits) throws IOException;
    /**
     * @param bits Reads 0-32 bits from the bitstream, but puts them back
     * @return the value of those bits
     */
    public int showBits(int bits) throws IOException;
    /**
     * @param bits Skips any number of bits from the bitstream
     * @return The number of bits skipped
     */
    public long skipBits(long bits) throws IOException;
    public long getBitsCount();
    public int readBytes(byte[] buffer, int offset, int length) throws IOException;

    public default int readBytes(byte[] buffer) throws IOException {
        return readBytes(buffer, 0, buffer.length);
    }

    public default boolean readBool() throws IOException {
        return readBits(1) != 0;
    }

    public default int readU32(int c0, int u0, int c1, int u1, int c2, int u2, int c3, int u3) throws IOException {
        int choice = readBits(2);
        int[] c = new int[]{c0, c1, c2, c3};
        int[] u = new int[]{u0, u1, u2, u3};
        return c[choice] + readBits(u[choice]);
    }

    public default long readU64() throws IOException {
        int index = readBits(2);
        if (index == 0)
            return 0L;
        if (index == 1)
            return 1L + readBits(4);
        if (index == 2)
            return 17L + readBits(8);
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

    public default float readF16() throws IOException {
        int bits16 = readBits(16);
        int mantissa = bits16 & 0x3FF;
        int biased_exp = (bits16 >>> 10) & 0x1F;
        // 143 == 31 + 127 - 15 == 31 + 112
        if (biased_exp == 31)
            throw new InvalidBitstreamException("Illegal infinite/NaN float16");
        biased_exp += 127 - 15;
        mantissa <<= 13;
        int sign = (bits16 & 0x8000) << 16;
        int total = sign | (biased_exp << 23) | mantissa;
        return Float.intBitsToFloat(total);
    }

    public default int readEnum() throws IOException {
        int constant = readU32(0, 0, 1, 0, 2, 4, 18, 6);
        if (constant > 63)
            throw new InvalidBitstreamException("Enum constant > 63");
        return constant;
    }

    /* used with ANS */
    public default int readU8() throws IOException {
        if (!readBool())
            return 0;
        int n = readBits(3);
        if (n == 0)
            return 1;
        return readBits(n) + (1 << n);
    }

    public default int readICCVarint() throws IOException {
        long value = 0;
        for (int shift = 0; shift < 63; shift += 7) {
            long b = readBits(8);
            value |= (b & 127L) << shift;
            if (b <= 127L)
                break;
        }
        if (value > Integer.MAX_VALUE)
            throw new InvalidBitstreamException("ICC Varint Overflow");
        return (int)value;
    }

    public default boolean atEnd() throws IOException {
        try {
            showBits(1);
        } catch (EOFException eof) {
            return true;
        }
        return false;
    }

    public void zeroPadToByte() throws IOException;
}
