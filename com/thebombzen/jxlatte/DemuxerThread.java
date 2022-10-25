package com.thebombzen.jxlatte;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DemuxerThread extends Thread {

    private static final byte[] CONTAINER_SIGNATURE = new byte[]{
        0x00, 0x00, 0x00, 0x0C, 'J', 'X', 'L', ' ', 0x0D, 0x0A, (byte)0x87, 0x0A
    };

    private static final byte[] JXLC = new byte[]{'j', 'x', 'l', 'c'};
    private static final byte[] JXLP = new byte[]{'j', 'x', 'l', 'p'};
    private static final byte[] JXLL = new byte[]{'j', 'x', 'l', 'l'};

    private InputStream in;
    private BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private Throwable error = null;
    private volatile int level = 5;

    public DemuxerThread(InputStream in) {
        this.in = in;
    }

    public BlockingQueue<byte[]> getQueue() {
        return queue;
    }

    public Throwable getLastError() {
        return error;
    }

    public int getLevel() {
        return level;
    }

    private void dummyDemux() throws Throwable {
        byte[] buffer = new byte[4096];
        while (true) {
            int count = in.read(buffer);
            if (count > 0) {
                byte[] buf = new byte[count];
                System.arraycopy(buffer, 0, buf, 0, count);
                queue.put(buf);
            } else {
                queue.put(new byte[0]);
                break;
            }
        }
    }

    // nonzero return value indicates how much wasn't read
    private int readFully(byte[] buffer) throws IOException {
        return readFully(buffer, 0, buffer.length);
    }

    private int readFully(byte[] buffer, int offset, int len) throws IOException {
        int remaining = len;
        while (remaining > 0) {
            int count = in.read(buffer, offset + len - remaining, remaining);
            if (count <= 0)
                break;
            remaining -= count;
        }
        return remaining;
    }

    private long skipFully(long n) throws IOException {
        
        long remaining = n;

        while (remaining > 0) {
            long skipped = in.skip(remaining);
            remaining -= skipped;
            if (skipped == 0)
                break;
        }

        if (remaining == 0) {
            return 0;
        }

        byte[] buffer = new byte[4096];

        while (remaining > buffer.length) {
            int k = readFully(buffer);
            if (k != 0)
                return -1;
            remaining -= buffer.length;
        }

        if (readFully(buffer, 0, (int)remaining) != 0)
            return -1;

        return 0;
    }

    private void containerDemux() throws Throwable {
        byte[] boxSize = new byte[4];
        byte[] boxTag = new byte[4];
        byte[] extendedSize = new byte[8];
        while (true) {
            int c = readFully(boxSize);
            if (c != 0) {
                if (c < 4)
                    throw new InvalidBitstreamException("Truncated box size");
                else
                    return; // eof
            }
            if (readFully(boxTag) != 0)
                throw new InvalidBitstreamException("Truncated box tag");
            long size = 0;
            for (int i = 0; i < 4; i++)
                size = (size << 8) | ((int)boxSize[i] & 0xFF);
            if (size == 1) {
                size = 0;
                if (readFully(extendedSize) != 0)
                    throw new InvalidBitstreamException("Truncated extended size");
                for (int i = 0; i < 8; i++)
                    size = (size << 8) | ((long)extendedSize[i] & 0xFFL);
                size -= 16;
            } else {
                size -= 8;
            }
            if (Arrays.equals(JXLL, boxTag)) {
                if (size != 1L)
                    throw new InvalidBitstreamException("jxll box must be size == 1");
                level = in.read();
                if (level != 5 && level != 10)
                    throw new InvalidBitstreamException(String.format("Invalid level: %d", level));
            }
            if (Arrays.equals(JXLP, boxTag)) {
                if (skipFully(4) < 0)
                    throw new InvalidBitstreamException("Truncated sequence number");
            }
            if (Arrays.equals(JXLP, boxTag) || Arrays.equals(JXLC, boxTag)) {
                if (size == 0) {
                    dummyDemux();
                    return;
                } else {
                    byte[] buffer = new byte[4096];
                    while (size > 0) {
                        int len = size > buffer.length ? buffer.length : (int)size;
                        int count = in.read(buffer, 0, len);
                        if (count > 0) {
                            byte[] buf = new byte[count];
                            System.arraycopy(buffer, 0, buf, 0, count);
                            queue.put(buf);
                            size -= count;
                        } else {
                            throw new InvalidBitstreamException("Premature end of box");
                        }
                    }
                }
            } else {
                if (size > 0) {
                    if (skipFully(size) < 0)
                        throw new InvalidBitstreamException("Truncated extra box");
                }
            }           
        }
    }

    private void run0() throws Throwable {
        byte[] signature = new byte[12];
        int remaining = readFully(signature);
        if (!Arrays.equals(signature, CONTAINER_SIGNATURE)) {
            if (remaining != 0) {
                // shorter than 12 bytes, kinda sus
                byte[] buf = new byte[signature.length - remaining];
                System.arraycopy(signature, 0, buf, 0, buf.length);
                signature = buf; 
            }
            queue.put(signature);
            dummyDemux();
        } else {
            containerDemux();
        }
    }

    @Override
    public void run() {
        try {
            run0();
        } catch (Throwable t) {
            this.error = t;
        } finally {
            try {
                in.close();
            } catch (IOException ex) {
                if (error == null)
                    error = ex;
            }
        }
    }
}

