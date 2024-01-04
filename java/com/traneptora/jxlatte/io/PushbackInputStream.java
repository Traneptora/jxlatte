package com.traneptora.jxlatte.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Supplier;

import com.traneptora.jxlatte.util.functional.ExceptionalSupplier;

public class PushbackInputStream extends InputStream {
    private byte[] buffer = null;
    private int bufferPos = 0;
    private Supplier<byte[]> supplier = null;
    private Queue<byte[]> fifo = new ArrayDeque<>();
    private InputStream in = null;

    public PushbackInputStream(ExceptionalSupplier<byte[]> supplier) {
        this.supplier = supplier;
    }

    public PushbackInputStream(InputStream in) {
        this.in = in;
    }

    public void pushBack(byte[] array) {
        fifo.add(array);
    }

    /* returns true upon EOF */
    private boolean refillBuffer() throws IOException {
        if (buffer == null || bufferPos >= buffer.length) {
            bufferPos = 0;
            buffer = fifo.poll();           
        }
        if (buffer == null && in == null)
            buffer = supplier.get();
        return buffer != null && buffer.length == 0;
    }

    @Override
    public int available() throws IOException {
        return buffer != null ? buffer.length - bufferPos : in != null ? in.available() : 0;
    }

    public byte[] drain() {
        if (buffer == null || bufferPos >= buffer.length)
            return null;
        byte[] result = new byte[buffer.length - bufferPos];
        System.arraycopy(buffer, bufferPos, result, 0, result.length);
        buffer = fifo.poll();
        bufferPos = 0;
        return result;
    }

    @Override
    public int read() throws IOException {
        if (refillBuffer())
            return -1;

        if (buffer == null)
            return in.read();

        return 0xFF & buffer[bufferPos++];
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int offset, int length) throws IOException {
        if (refillBuffer())
            return -1;
        
        if (buffer == null)
            return in.read(b, offset, length);

        int count = available();
        if (count > length)
            count = length;
        System.arraycopy(buffer, bufferPos, b, offset, count);
        bufferPos += count;
        return count;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0)
            return 0;
        if (refillBuffer())
            return 0;
        if (buffer == null) {
            try {
                long count = Math.min(n, in.available());
                return in.skip(count);
            } catch (IOException ioe) {
                if (ioe.getMessage().equals("Illegal seek"))
                    return 0;
                throw ioe;
            }
        }

        int count = available();
        if (count > n)
            count = (int)n;
        bufferPos += count;
        return count;        
    }

    @Override
    public void close() throws IOException {
        if (in != null)
            in.close();
    }
}
