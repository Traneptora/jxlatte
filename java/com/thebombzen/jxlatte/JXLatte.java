package com.thebombzen.jxlatte;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;

import com.thebombzen.jxlatte.io.ByteArrayQueueInputStream;
import com.thebombzen.jxlatte.io.InputStreamBitreader;
import com.thebombzen.jxlatte.io.PNGWriter;

public class JXLatte {

    private DemuxerThread demuxerThread;
    private JXLCodestreamDecoder decoder;

    public JXLatte(InputStream in) {
        this.demuxerThread = new DemuxerThread(in);
        demuxerThread.start();
        this.decoder = new JXLCodestreamDecoder(
            new InputStreamBitreader(
            new ByteArrayQueueInputStream(
            demuxerThread.getQueue())));
    }

    public JXLatte(String filename) throws FileNotFoundException {
        this(new BufferedInputStream(new FileInputStream(filename)));
    }

    public JXLImage decode() throws IOException {
        IOException error = null;
        JXLImage ret = null;
        try {
            ret = decoder.decode(demuxerThread.getLevel());;
        } catch (IOException ex) {
            error = ex;
        }
        if (demuxerThread.getLastError() != null)
            throw new IOException(demuxerThread.getLastError());
        if (error != null)
            throw error;
        return ret;
    }

    public static void main(String[] args) throws Throwable {
        String inputFilename = args.length > 0 ? args[0] : "samples/lenna.jxl";
        String outputFilename = args.length > 1 ? args[1] : "output.png";
        JXLatte jxlatte = new JXLatte(inputFilename);
        JXLImage image = jxlatte.decode();
        PNGWriter writer = new PNGWriter(image, 8);
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFilename))) {
            writer.write(out, Deflater.BEST_SPEED);
        }
    }
}
