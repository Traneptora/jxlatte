package com.thebombzen.jxlatte;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;

public class JXLatte {

    private ReaderThread readerThread;
    private JXLDecoder decoder;

    public JXLatte(InputStream in) {
        this.readerThread = new ReaderThread(in);
        readerThread.start();
        this.decoder = new JXLDecoder(new InputStreamBitreader(new ByteArrayQueueInputStream(readerThread.getQueue())));
    }

    public JXLatte(String filename) throws FileNotFoundException {
        this(new BufferedInputStream(new FileInputStream(filename)));
    }

    public BufferedImage decode() throws IOException {
        IOException error = null;
        BufferedImage ret = null;
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
            args = new String[]{"bbb.jxl"};
        }
        JXLatte jxlatte = new JXLatte(args[0]);
        BufferedImage image = jxlatte.decode();
        System.out.format("width, height: %d, %d%n", image.getWidth(), image.getHeight());
    }
}
