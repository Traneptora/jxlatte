package com.traneptora.jxlatte;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.traneptora.jxlatte.bundle.ImageHeader;
import com.traneptora.jxlatte.io.Demuxer;
import com.traneptora.jxlatte.io.PushbackInputStream;

public class JXLDecoder implements Closeable {
    private Demuxer demuxer;
    private JXLCodestreamDecoder decoder;
    private boolean headerRead = false;

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

    /**
     * Reads the image header (size, bit depth, channels, color encoding, ICC profile etc.), without decoding the image.
     * A subsequent call to {@link #decode()} continues from the header, and decodes the corresponding image.
     *
     * @return the image header
     */
    public ImageHeader readHeader() throws IOException {
        if (!headerRead) {
            demuxer.reset();
            headerRead = true;
        }
        return decoder.readHeader();
    }

    public JXLImage decode() throws IOException {
        // don't reset the demuxer if the header was already read, as decoding continues after the header
        if (!headerRead)
            demuxer.reset();
        headerRead = false;
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
