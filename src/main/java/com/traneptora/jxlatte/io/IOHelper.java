package com.traneptora.jxlatte.io;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class IOHelper {

    /**
     * Reads exactly {@code length} bytes. Unlike allocating a buffer of {@code length} bytes up front,
     * memory is only allocated as data is actually read, so a corrupt length can't cause huge allocations.
     *
     * @throws EOFException if fewer than {@code length} bytes are available
     */
    public static byte[] readBytes(InputStream in, int length) throws IOException {
        if (length < 0)
            throw new InvalidBitstreamException("Negative length: " + length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(length, 8192));
        byte[] buffer = new byte[Math.min(length, 8192)];
        int remaining = length;
        while (remaining > 0) {
            int count = in.read(buffer, 0, Math.min(buffer.length, remaining));
            if (count <= 0)
                throw new EOFException(String.format("Expected %d bytes, got %d", length, length - remaining));
            out.write(buffer, 0, count);
            remaining -= count;
        }
        return out.toByteArray();
    }

    /**
     * @return How much wasn't read due to EOF
     */
    public static int readFully(InputStream in, byte[] buffer) throws IOException {
        return readFully(in, buffer, 0, buffer.length);
    }

    /**
     * @return How much wasn't read due to EOF
     */
    public static int readFully(InputStream in, byte[] buffer, int offset, int len) throws IOException {
        int remaining = len;
        while (remaining > 0) {
            int count = in.read(buffer, offset + len - remaining, remaining);
            if (count <= 0)
                break;
            remaining -= count;
        }
        return remaining;
    }

    /** 
     * @return How much wasn't read due to EOF
     */
    public static long skipFully(InputStream in, long n) throws IOException {
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
            int k = readFully(in, buffer);
            remaining = remaining - buffer.length + k;
            if (k != 0)
                return remaining;
        }

        return readFully(in, buffer, 0, (int)remaining);
    }

    private IOHelper(){}
}
