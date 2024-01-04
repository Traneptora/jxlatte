package com.traneptora.jxlatte.io;

import java.io.IOException;
import java.io.InputStream;

public final class IOHelper {

    private IOHelper(){
        
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
}
