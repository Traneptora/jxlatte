package com.traneptora.jxlatte.bundle;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import com.traneptora.jxlatte.io.Bitreader;

public class Extensions {
    public final long extensionsKey;
    private byte[][] payloads = new byte[64][];

    public Extensions() {
        extensionsKey = 0;
    }

    public Extensions(Bitreader reader) throws IOException {
        extensionsKey = reader.readU64();
        for (int i = 0; i < 64; i++) {
            if (((1L << i) & extensionsKey) != 0) {
                long length = reader.readU64();
                if (length > Integer.MAX_VALUE)
                    throw new UnsupportedOperationException("Large extensions unsupported");
                payloads[i] = new byte[(int)length];
            }
        }
        for (int i = 0; i < 64; i++) {
            if (payloads[i] != null) {
                for (int j = 0; j < payloads[i].length; j++) {
                    payloads[i][j] = (byte)reader.readBits(8);
                }
            }
        }
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
