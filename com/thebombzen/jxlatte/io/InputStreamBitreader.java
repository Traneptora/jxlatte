package com.thebombzen.jxlatte.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import com.thebombzen.jxlatte.InvalidBitstreamException;

public class InputStreamBitreader implements Bitreader {

    private InputStream in;
    private long cache = 0;
    private int cache_bits = 0;
    private long bitsRead = 0;

    public InputStreamBitreader(InputStream in) {
        this.in = in;
    }

    @Override
    public int readBits(int bits) throws IOException {
        if (bits == 0)
            return 0;
        if (bits < 0 || bits > 32)
            throw new IllegalArgumentException("Must read between 0-32 bits, inclusive");
        if (bits <= cache_bits) {
            int ret = (int)(cache & ~(~0L << bits));
            cache_bits -= bits;
            cache >>>= bits;
            bitsRead += bits;
            return ret;
        }
        int count = in.available();
        int max = (64 - cache_bits) / 8;
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

    @Override
    public int showBits(int bits) throws IOException {
        int n = readBits(bits);
        bitsRead -= bits;
        cache = (cache << bits) | (n & 0xFFFFFFFFL);
        cache_bits += bits;
        return n;
    }

    @Override
    public void zeroPadToByte() throws IOException {
        int remaining = cache_bits % 8;
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
    public long skipBits(long bits) throws IOException {
        if (bits < 0)
            throw new IllegalArgumentException();
        if (bits == 0)
            return 0;
        if (bits <= cache_bits) {
            cache_bits -= bits;
            bitsRead += bits;
            return bits;
        }
        long skipped = bits - IOHelper.skipFully(in, bits - cache_bits);
        cache_bits = 0;
        bitsRead += skipped;
        return skipped;
    }

    public long getBitsCount() {
        return bitsRead;
    }
}
