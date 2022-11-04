package com.thebombzen.jxlatte;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.thebombzen.jxlatte.io.ByteArrayQueueInputStream;
import com.thebombzen.jxlatte.io.Demuxer;
import com.thebombzen.jxlatte.io.InputStreamBitreader;
import com.thebombzen.jxlatte.io.PNGWriter;

public class JXLatte {

    private Demuxer demuxer;
    private JXLCodestreamDecoder decoder;

    public JXLatte(InputStream in) {
        demuxer = new Demuxer(in);
        new Thread(demuxer).start();
        decoder = new JXLCodestreamDecoder(
            new InputStreamBitreader(new ByteArrayQueueInputStream(demuxer.getQueue())));
    }

    public JXLatte(String filename) throws FileNotFoundException {
        this(new BufferedInputStream(new FileInputStream(filename)));
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

    public static void main(String[] args) throws Throwable {
        String inputFilename = args.length > 0 ? args[0] : "samples/lenna.jxl";
        String outputFilename = args.length > 1 ? args[1] : "output.png";
        JXLatte jxlatte = new JXLatte(inputFilename);
        JXLImage image = jxlatte.decode();
        PNGWriter writer = new PNGWriter(image);
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFilename))) {
            writer.write(out);
        }
    }
}
