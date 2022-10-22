package com.thebombzen.jxlatte;

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
    public void zeroPadToByte() throws IOException, InvalidBitstreamException {
        this.bitreader.zeroPadToByte();
    }

    @Override
    public void close() throws IOException {
        this.bitreader.close();
    }

}
