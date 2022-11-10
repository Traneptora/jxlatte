package com.thebombzen.jxlatte;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.stream.Stream;

import com.thebombzen.jxlatte.io.ByteArrayQueueInputStream;
import com.thebombzen.jxlatte.io.Demuxer;
import com.thebombzen.jxlatte.io.InputStreamBitreader;
import com.thebombzen.jxlatte.io.PNGWriter;

public class JXLatte {

    public static final String JXLATTE_VERSION = "0.0.2";

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
        if (args.length == 0) {
            String[] lines = new String[]{
                "jxlatte, version: " + JXLATTE_VERSION,
                "Usage: java -jar jxlatte.jar <input.jxl> [output.png]",
                "",
                "If the output filename is not provided, jxlatte will discard the decoded pixels.",
            };
            Stream.of(lines).forEach(System.out::println);
            System.exit(1);
        }
        String inputFilename = args[0];
        String outputFilename = args.length > 1 ? args[1] : null;
        JXLatte jxlatte = new JXLatte(inputFilename);
        JXLImage image = jxlatte.decode();
        if (outputFilename != null) {
            System.err.println("Decoded to pixels, writing PNG output.");
            PNGWriter writer = new PNGWriter(image);
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFilename))) {
                writer.write(out);
            }
        } else {
            System.err.println("Decoded to pixels, discarding output.");
        }
    }
}
