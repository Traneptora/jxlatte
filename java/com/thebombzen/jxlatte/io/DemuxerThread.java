package com.thebombzen.jxlatte.io;

import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.util.FunctionalHelper;

public class DemuxerThread extends Thread {

    private static final byte[] CONTAINER_SIGNATURE = new byte[]{
        0x00, 0x00, 0x00, 0x0C, 'J', 'X', 'L', ' ', 0x0D, 0x0A, (byte)0x87, 0x0A
    };

    private static final int JXLC = (int)makeTag(new byte[]{'j', 'x', 'l', 'c'});
    private static final int JXLP = (int)makeTag(new byte[]{'j', 'x', 'l', 'p'});
    private static final int JXLL = (int)makeTag(new byte[]{'j', 'x', 'l', 'l'});

    public static long makeTag(byte[] tagArray, int offset, int length) {
        long tag = 0;
        for (int i = offset; i < offset + length; i++)
            tag = (tag << 8) | ((int)tagArray[i] & 0xFF);
        return tag;
    }

    public static long makeTag(byte[] tagArray) {
        return makeTag(tagArray, 0, tagArray.length);
    }

    private InputStream in;
    private BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private CompletableFuture<Integer> level = new CompletableFuture<>();
    private CompletableFuture<Void> exception = new CompletableFuture<>();

    public DemuxerThread(InputStream in) {
        this.in = in;
    }

    public BlockingQueue<byte[]> getQueue() {
        return queue;
    }


    public void joinExceptionally() {
        try {
            exception.join();
        } catch (CompletionException ex) {
            FunctionalHelper.sneakyThrow(ex.getCause());
        }
    }

    public int getLevel() {
        return level.join();
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

    private void containerDemux() throws Throwable {
        byte[] boxSize = new byte[8];
        byte[] boxTag = new byte[4];
        while (true) {
            int c = IOHelper.readFully(in, boxSize, 0, 4);
            if (c != 0) {
                if (c < 4) {
                    throw new InvalidBitstreamException("Truncated box size");
                } else {
                    queue.put(new byte[0]);
                    return;
                }
            }
            long size = makeTag(boxSize, 0, 4);
            if (size == 1) {
                if (IOHelper.readFully(in, boxSize, 0, 8) != 0)
                    throw new InvalidBitstreamException("Truncated extended size");
                size = makeTag(boxSize, 0, 8);
                if (size > 0)
                    size -= 8;
            }
            if (IOHelper.readFully(in, boxTag) != 0)
                throw new InvalidBitstreamException("Truncated box tag");
            int tag = (int)makeTag(boxTag);
            if (size > 0)
                size -= 8;
            if (size < 0)
                throw new InvalidBitstreamException("Illegal box size");
            if (tag == JXLL) {
                if (size != 1L)
                    throw new InvalidBitstreamException("jxll box must be size == 1");
                int l = in.read();
                if (l != 5 && l != 10)
                    throw new InvalidBitstreamException(String.format("Invalid level: %d", level));
                level.complete(l);
            }
            boolean finalImageBox = tag == JXLC;
            if (tag == JXLP) {
                if (IOHelper.readFully(in, boxTag) != 0)
                    throw new InvalidBitstreamException("Truncated sequence number");
                int sequenceNumber = (int)makeTag(boxTag);
                finalImageBox = (sequenceNumber & 0x80_00_00_00) != 0;
                size -= 4;
            }
            if (tag == JXLP || tag == JXLC) {
                if (!level.isDone())
                    level.complete(5);
                if (size == 0) {
                    /* box lasts until EOF */
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
                if (finalImageBox) {
                    queue.put(new byte[0]);
                    return;
                }
            } else {
                if (size > 0) {
                    if (IOHelper.skipFully(in, size) != 0)
                        throw new InvalidBitstreamException("Truncated extra box");
                }
            }           
        }
    }

    private void run0() throws Throwable {
        byte[] signature = new byte[12];
        int remaining = IOHelper.readFully(in, signature);
        if (!Arrays.equals(signature, CONTAINER_SIGNATURE)) {
            level.complete(5);
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
            exception.complete(null);
        } catch (Throwable ex) {
            exception.completeExceptionally(ex);
        } finally {
            level.complete(5);
            try {
                in.close();
            } catch (Throwable ex) {
                exception.completeExceptionally(ex);
            }
        }
    }
}
