package com.traneptora.jxlatte;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.traneptora.jxlatte.io.Demuxer;
import com.traneptora.jxlatte.io.PushbackInputStream;

public class JXLDecoder implements Closeable {
    private Demuxer demuxer;
    private JXLCodestreamDecoder decoder;

    public JXLDecoder(String filename) throws FileNotFoundException {
        this(filename, new JXLOptions());
    }

    public JXLDecoder(InputStream in) {
        this(in, new JXLOptions());
    }

    public JXLDecoder(String filename, JXLOptions options) throws FileNotFoundException {
        this(new BufferedInputStream(new FileInputStream(filename)), options);
    }

    public JXLDecoder(InputStream in, JXLOptions options) {
        demuxer = new Demuxer(in);
        decoder = new JXLCodestreamDecoder(new PushbackInputStream(demuxer), options, demuxer);
    }

    public JXLImage decode() throws IOException {
        demuxer.reset();
        return decoder.decode();
    }

    public boolean atEnd() throws IOException {
        return decoder.atEnd();
    }

    @Override
    public void close() throws IOException {
        demuxer.close();
    }
}
