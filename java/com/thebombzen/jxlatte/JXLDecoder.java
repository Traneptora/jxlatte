package com.thebombzen.jxlatte;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.thebombzen.jxlatte.io.ByteArrayQueueInputStream;
import com.thebombzen.jxlatte.io.Demuxer;
import com.thebombzen.jxlatte.io.InputStreamBitreader;

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
        new Thread(demuxer).start();
        decoder = new JXLCodestreamDecoder(
            new InputStreamBitreader(new ByteArrayQueueInputStream(demuxer.getQueue())), options);
    }

    public JXLDecoder(String filename, Options options) throws FileNotFoundException {
        this(new BufferedInputStream(new FileInputStream(filename)), options);
    }

    public JXLImage decode() throws IOException {
        IOException error = null;
        JXLImage ret = null;
        try {
            demuxer.checkException();
            ret = decoder.decode(demuxer.getLevel());
        } catch (IOException ex) {
            error = ex;
        }
        demuxer.joinExceptionally();
        if (error != null)
            throw error;
        return ret;
    }
}
