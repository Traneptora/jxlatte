package com.thebombzen.jxlatte;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.thebombzen.jxlatte.image.JxlImage;
import com.thebombzen.jxlatte.io.ByteArrayQueueInputStream;
import com.thebombzen.jxlatte.io.InputStreamBitreader;

public class JXLatte {

    private ReaderThread readerThread;
    private JXLCodestreamDecoder decoder;

    public JXLatte(InputStream in) {
        this.readerThread = new ReaderThread(in);
        readerThread.start();
        this.decoder = new JXLCodestreamDecoder(
            new InputStreamBitreader(
            new ByteArrayQueueInputStream(
            readerThread.getQueue())));
    }

    public JXLatte(String filename) throws FileNotFoundException {
        this(new BufferedInputStream(new FileInputStream(filename)));
    }

    public JxlImage decode() throws IOException {
        IOException error = null;
        JxlImage ret = null;
        try {
            ret = decoder.decode();
        } catch (IOException ex) {
            error = ex;
        }
        if (readerThread.getLastError() != null)
            throw new IOException(readerThread.getLastError());
        if (error != null)
            throw error;
        return ret;
    }

    public static void main(String[] args) throws Throwable {
        if (args.length == 0) {
            // System.err.println("Usage: jxlatte <input.jxl>");
            // System.exit(1);
            args = new String[]{"samples/bench.jxl"};
        }
        JXLatte jxlatte = new JXLatte(args[0]);
        JxlImage image = jxlatte.decode();
        System.out.format("width, height: %d, %d%n", image.getWidth(), image.getHeight());
    }
}
