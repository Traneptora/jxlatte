package com.traneptora.jxlatte.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import com.traneptora.jxlatte.util.functional.ExceptionalSupplier;

public class Demuxer implements ExceptionalSupplier<byte[]>, Closeable {

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

    private PushbackInputStream in;
    private int level = 5;
    private boolean foundSignature = false;
    private long posInBox = 0;
    private long boxSize;
    private boolean container;

    public Demuxer(InputStream in) {
        this.in = new PushbackInputStream(in);
    }

    public int getLevel() {
        return level;
    }

    public void reset() {
        level = 5;
        foundSignature = false;
        posInBox = 0;
    }

    public void pushBack(byte[] buffer) {
        in.pushBack(buffer);
    }

    private byte[] containerDemux() throws IOException {
        byte[] boxSizeArray = new byte[8];
        byte[] boxTag = new byte[4];
        while (true) {
            int c = IOHelper.readFully(in, boxSizeArray, 0, 4);
            if (c != 0) {
                if (c < 4)
                    throw new InvalidBitstreamException("Truncated box size");
                
                return new byte[0];
            }
            boxSize = makeTag(boxSizeArray, 0, 4);
            if (boxSize == 1) {
                if (IOHelper.readFully(in, boxSizeArray, 0, 8) != 0)
                    throw new InvalidBitstreamException("Truncated extended size");
                    boxSize = makeTag(boxSizeArray, 0, 8);
                if (boxSize > 0)
                    boxSize -= 8;
            }
            if (boxSize > 0)
                boxSize -= 8;
            if (boxSize < 0)
                throw new InvalidBitstreamException("Illegal box size");
            if (IOHelper.readFully(in, boxTag) != 0)
                throw new InvalidBitstreamException("Truncated box tag");
            int tag = (int)makeTag(boxTag);
            if (tag == JXLL) {
                if (boxSize != 1L)
                    throw new InvalidBitstreamException("jxll box must be size == 1");
                int l = in.read();
                if (l != 5 && l != 10)
                    throw new InvalidBitstreamException(String.format("Invalid level: %d", level));
                level = l;
                continue;
            }
            if (tag == JXLP) {
                if (IOHelper.readFully(in, boxTag) != 0)
                    throw new InvalidBitstreamException("Truncated sequence number");
                boxSize -= 4;
            }
            if (tag == JXLP || tag == JXLC) {
                posInBox = 0;
                return supplyExceptionally();
            } else {
                if (boxSize > 0) {
                    long s = IOHelper.skipFully(in, boxSize);
                    if (s != 0)
                        throw new InvalidBitstreamException("Truncated extra box");
                } else {
                    return supplyExceptionally();
                }
            }           
        }
    }

    @Override
    public byte[] supplyExceptionally() throws IOException {

        if (!foundSignature) {
            byte[] signature = new byte[12];
            int remaining = IOHelper.readFully(in, signature);
            foundSignature = true;
            if (!Arrays.equals(signature, CONTAINER_SIGNATURE)) {
                if (remaining != 0) {
                    // shorter than 12 bytes, kinda sus
                    byte[] buf = new byte[signature.length - remaining];
                    System.arraycopy(signature, 0, buf, 0, buf.length);
                    signature = buf;
                }
                boxSize = 0;
                posInBox = signature.length;
                container = false;
                return signature;
            } else {
                boxSize = 12;
                posInBox = 12;
                container = true;
            }
        }

        if (!container || boxSize > 0 && posInBox < boxSize || boxSize == 0) {
            int len = 4096;
            if (boxSize > 0 && boxSize - posInBox < len)
                len = (int)(Math.min(Integer.MAX_VALUE, boxSize - posInBox));
            byte[] buf = new byte[len];
            int remaining = IOHelper.readFully(in, buf);
            posInBox += len - remaining;
            if (remaining > 0) {
                if (remaining == len)
                    return new byte[0];
                byte[] b2 = new byte[len - remaining];
                System.arraycopy(buf, 0, b2, 0, b2.length);
                buf = b2;
            }
            return buf;
        }

        return containerDemux();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
