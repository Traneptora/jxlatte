package com.thebombzen.jxlatte.io;

import java.io.IOException;

public class BitreaderBitreader implements Bitreader {

    private Bitreader bitreader;

    public BitreaderBitreader(Bitreader bitreader) {
        this.bitreader = bitreader;
    }

    @Override
    public int readBits(int bits) throws IOException {
        return this.bitreader.readBits(bits);
    }

    @Override
    public void zeroPadToByte() throws IOException {
        this.bitreader.zeroPadToByte();
    }

    @Override
    public void close() throws IOException {
        this.bitreader.close();
    }

    @Override
    public int showBits(int bits) throws IOException {
        return this.bitreader.showBits(bits);
    }

}
