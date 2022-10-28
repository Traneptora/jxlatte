package com.thebombzen.jxlatte.bundle;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

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
}
