package com.traneptora.jxlatte.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.util.MathHelper;

public class Bitreader extends InputStream {

    private InputStream in;
    private long cache = 0;
    private int cacheBits = 0;
    private long bitsRead = 0;

    public Bitreader(InputStream in) {
        this.in = in;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    public boolean readBool() throws IOException {
        return readBits(1) != 0;
    }

    public int readU32(int c0, int u0, int c1, int u1, int c2, int u2, int c3, int u3) throws IOException {
        int choice = readBits(2);
        int[] c = new int[]{c0, c1, c2, c3};
        int[] u = new int[]{u0, u1, u2, u3};
        return c[choice] + readBits(u[choice]);
    }

    public long readU64() throws IOException {
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

    public float readF16() throws IOException {
        int bits16 = readBits(16);
        float f = MathHelper.floatFromF16(bits16);
        if (!Float.isFinite(f))
            throw new InvalidBitstreamException("Illegal infinite/NaN float16");
        return f;
    }

    public int readEnum() throws IOException {
        int constant = readU32(0, 0, 1, 0, 2, 4, 18, 6);
        if (constant > 63)
            throw new InvalidBitstreamException("Enum constant > 63");
        return constant;
    }

    /* used with ANS */
    public int readU8() throws IOException {
        if (!readBool())
            return 0;
        int n = readBits(3);
        if (n == 0)
            return 1;
        return readBits(n) + (1 << n);
    }

    public int readICCVarint() throws IOException {
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

    public boolean atEnd() throws IOException {
        try {
            showBits(1);
        } catch (EOFException eof) {
            return true;
        }
        return false;
    }

    /**
     * @param bits Reads 0-32 bits from the bitstream
     * @return the value of those bits
     */
    public int readBits(int bits) throws IOException {
        if (bits == 0)
            return 0;
        if (bits < 0 || bits > 32)
            throw new IllegalArgumentException("Must read between 0-32 bits, inclusive");
        if (bits <= cacheBits) {
            int ret = (int)(cache & ~(~0L << bits));
            cacheBits -= bits;
            cache >>>= bits;
            bitsRead += bits;
            return ret;
        }
        int count = in.available();
        int max = (64 - cacheBits) / 8;
        count = count > 0 ? (count < max ? count : max) : 1;
        boolean eof = false;
        for (int i = 0; i < count; i++) {
            int b = in.read();
            if (b < 0) {
                eof = true;
                break;
            }
            cache |= (b & 0xFFL) << cacheBits;
            cacheBits += 8;
        }
        if (eof && bits > cacheBits)
            throw new EOFException("Unable to read enough bits: " + (getBitsCount() + bits));
        return readBits(bits);
    }

    /**
     * @param bits Reads 0-32 bits from the bitstream, but puts them back
     * @return the value of those bits
     */
    public int showBits(int bits) throws IOException {
        int n = 0;
        while (bits > 0) {
            try {
                n = readBits(bits);
                break;
            } catch (EOFException eof) {
                if (bits-- == 1)
                    throw eof;
            }
        }
        bitsRead -= bits;
        cache = (cache << bits) | (n & ~(~0L << bits));
        cacheBits += bits;
        return n;
    }

    public void zeroPadToByte() throws IOException {
        int remaining = cacheBits % 8;
        if (remaining > 0) {
            int padding = readBits(remaining);
            if (padding != 0)
                throw new InvalidBitstreamException("Nonzero zero-padding-to-byte");
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public long skip(long bytes) throws IOException {
        return skipBits(bytes << 3) >> 3;
    }

    /**
     * @param bits Skips any number of bits from the bitstream
     * @return The number of bits skipped
     */
    public long skipBits(long bits) throws IOException {
        if (bits < 0)
            throw new IllegalArgumentException();
        if (bits == 0)
            return 0;
        if (bits <= cacheBits) {
            cacheBits -= bits;
            cache >>>= bits;
            bitsRead += bits;
            return bits;
        }
        long cacheSave = cacheBits;
        skipBits(cacheBits);
        bits -= cacheSave;
        long dangler = bits % 8L;
        long skipped = bits - dangler - 8L * IOHelper.skipFully(in, (bits - dangler) / 8L);
        bitsRead += skipped;
        skipped += cacheSave;
        readBits((int)dangler);
        return skipped + dangler;
    }

    public long getBitsCount() {
        return bitsRead;
    }

    @Override
    public int read() throws IOException {
        try {
            return readBits(8);
        } catch (EOFException eof) {
            return -1;
        }
    }

    public byte[] drainCache() throws IOException {
        if (cacheBits % 8 != 0)
            throw new IllegalStateException("You must align before drainCache");
        int cacheBytes = cacheBits / 8;
        if (cacheBytes == 0)
            return null;
        byte[] buffer = new byte[cacheBytes];
        read(buffer);
        return buffer;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0)
            return 0;
        if (cacheBits % 8 != 0)
            throw new IllegalStateException("You must align before readBytes");
        int cacheBytes = cacheBits / 8;
        for (int i = 0; i < cacheBytes; i++) {
            if (length-- < 1)
                return i;
            buffer[offset + i] = (byte)readBits(8);
        }
        int remaining = IOHelper.readFully(in, buffer, offset + cacheBytes, length);
        bitsRead += (length - remaining) * 8L;
        int ret = cacheBytes + length - remaining;
        if (ret == 0)
            return -1;
        return ret;
    }
}
