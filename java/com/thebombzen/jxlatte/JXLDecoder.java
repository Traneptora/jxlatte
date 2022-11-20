package com.thebombzen.jxlatte;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.thebombzen.jxlatte.io.Demuxer;
import com.thebombzen.jxlatte.io.PushbackInputStream;

public class JXLDecoder {
    private Demuxer demuxer;
    private JXLCodestreamDecoder decoder;

    public JXLDecoder(InputStream in) {
        this(in, new Options());
    }

    public JXLDecoder(String filename) throws FileNotFoundException {
        this(filename, new Options());
    }

    public JXLDecoder(InputStream in, Options options) {
        demuxer = new Demuxer(in);
        decoder = new JXLCodestreamDecoder(new PushbackInputStream(demuxer), options, demuxer);
    }

    public JXLDecoder(String filename, Options options) throws FileNotFoundException {
        this(new BufferedInputStream(new FileInputStream(filename)), options);
    }

    public JXLImage decode() throws IOException {
        demuxer.reset();
        return decoder.decode();
    }
}
