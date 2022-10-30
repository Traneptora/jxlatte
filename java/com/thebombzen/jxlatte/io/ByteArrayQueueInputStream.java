package com.thebombzen.jxlatte.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;

public class ByteArrayQueueInputStream extends InputStream {
    private BlockingQueue<byte[]> queue;
    private byte[] buffer = null;
    private int bufferPos = 0;

    public ByteArrayQueueInputStream(BlockingQueue<byte[]> queue) {
        this.queue = queue;
    }

    /* returns true upon EOF */
    private boolean refillBuffer() throws IOException {
        if (buffer == null || bufferPos >= buffer.length) {
            try {
                buffer = queue.take();
                bufferPos = 0;
            } catch (InterruptedException ie) {
                throw new IOException("Interrupted while taking from queue", ie);
            }
        }
        return buffer.length == 0;
    }

    @Override
    public int available() {
        return buffer != null ? buffer.length - bufferPos : 0;
    }

    @Override
    public int read() throws IOException {
        if (refillBuffer())
            return -1;
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
        int count = available();
        if (count > length)
            count = length;
        System.arraycopy(buffer, bufferPos, b, offset, count);
        bufferPos += count;
        return count;
    }
}
