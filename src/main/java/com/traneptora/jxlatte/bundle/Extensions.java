package com.traneptora.jxlatte.bundle;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.IOHelper;

public class Extensions {
    public final long extensionsKey;
    private byte[][] payloads;

    public Extensions() {
        extensionsKey = 0;
        payloads = new byte[64][];
    }

    public Extensions(long extensionsKey, byte[][] payloads) {
        if (payloads == null || payloads.length != 64)
            throw new IllegalArgumentException();
        for (int i = 0; i < 64; i++) {
            boolean nze = ((1L << i) & extensionsKey) != 0;
            if (nze != (payloads[i] != null))
                throw new IllegalArgumentException();
        }
        this.extensionsKey = extensionsKey;
        this.payloads = payloads;
    }

    public static Extensions readExtensions(Bitreader reader) throws IOException {
        long extensionsKey = reader.readU64();
        long[] lengths = new long[64];
        for (int i = 0; i < 64; i++) {
            if (((1L << i) & extensionsKey) != 0) {
                lengths[i] = reader.readU64();
                if (lengths[i] > Integer.MAX_VALUE)
                    throw new UnsupportedOperationException("Large extensions unsupported");
            }
        }
        // Don't allocate the payloads from the (untrusted) lengths up front
        byte[][] payloads = new byte[64][];
        for (int i = 0; i < 64; i++) {
            if (((1L << i) & extensionsKey) != 0)
                payloads[i] = IOHelper.readBytes(reader, (int)lengths[i]);
        }
        return new Extensions(extensionsKey, payloads);
    }

    public byte[] getExtension(int extId) {
        return payloads[extId];
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.deepHashCode(payloads);
        result = prime * result + Objects.hash(extensionsKey);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Extensions other = (Extensions) obj;
        return extensionsKey == other.extensionsKey && Arrays.deepEquals(payloads, other.payloads);
    }

    @Override
    public String toString() {
        return "Extensions [extensionsKey=" + extensionsKey + "]";
    }
}
