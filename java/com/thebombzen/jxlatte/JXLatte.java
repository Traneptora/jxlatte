package com.thebombzen.jxlatte;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.thebombzen.jxlatte.io.PFMWriter;
import com.thebombzen.jxlatte.io.PNGWriter;

public class JXLatte {

    public static final String JXLATTE_VERSION = "0.0.2";

    private static void writePNG(String outputFilename, JXLImage image, int depth) throws IOException {
        PNGWriter writer = depth > 0 ? new PNGWriter(image, depth) : new PNGWriter(image);
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFilename))) {
            writer.write(out);
        }
    }

    private static void writePFM(String outputFilename, JXLImage image) throws IOException {
        PFMWriter writer = new PFMWriter(image);
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFilename))) {
            writer.write(out);
        }
    }

    private static void usage(boolean success) {
        String[] lines = new String[]{
            "jxlatte, version: " + JXLATTE_VERSION,
            "Usage: java -jar jxlatte.jar [options...] [--] <input.jxl> [output]",
            "",
            "Options: ",
            "    --help                       print this message",
            "    --output-png-depth=N         use N-bit output for PNG,",
            "                                     N must be 8 or 16",
            "    --output-format=<png|pfm>    write image in this output format",
            "",
            "If the output filename is not provided, jxlatte will discard the decoded pixels.",
        };
        System.err.println(String.join(String.format("%n"), lines));
        System.exit(success ? 0 : 1);
    }

    public static void main(String[] args) throws Throwable {
        if (args.length == 0) {
            usage(false);
        }
        String inputFilename = null;
        String outputFilename = null;
        boolean foundMM = false;
        int outputDepth = -1;
        int outputFormat = -1;
        for (String arg : args) {
            if (foundMM || !arg.startsWith("--")) {
                if (inputFilename == null) {
                    inputFilename = arg;
                    continue;
                }
                if (outputFilename == null) {
                    outputFilename = arg;
                    continue;
                }
                System.err.format("jxlatte: invalid trailing argument: %s%n", arg);
                System.exit(2);
            }
            if (arg.equals("--")) {
                foundMM = true;
                continue;
            }
            int indexEq = arg.indexOf("=");
            String key = indexEq >= 0 ? arg.substring(2, indexEq) : arg.substring(2);
            String value = indexEq >= 0 ? arg.substring(indexEq + 1) : "";
            switch (key) {
                case "help":
                    usage(true);
                case "output-png-depth":
                    try {
                        outputDepth = Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        System.err.format("jxlatte: not a number: %s%n", value);
                        System.exit(2);
                    }
                    if (outputDepth != 8 && outputDepth != 16) {
                        System.err.println("jxlatte: only 8-bit and 16-bit outputs supported in PNG");
                        System.exit(2);
                    }
                    break;
                case "output-format":
                    switch (value.toLowerCase()) {
                        case "png":
                            outputFormat = 0;
                            break;
                        case "pfm":
                            outputFormat = 1;
                            break;
                        default:
                            System.err.format("jxlatte: unknown output format: %s%n", value);
                            System.exit(2);
                    }
                    break;
                default:
                    System.err.format("jxlatte: unknown arg: %s%n", arg);
                    System.exit(2);
            }
        }
        if (inputFilename == null)
            usage(false);
        JXLDecoder decoder = inputFilename.equals("-")
            ? new JXLDecoder(System.in)
            : new JXLDecoder(inputFilename);
        JXLImage image = decoder.decode();
        if (outputFilename != null) {
            if (outputFormat < 0) {
                int idx = outputFilename.lastIndexOf('.');
                if (idx >= 0) {
                    String ext = outputFilename.substring(idx + 1).toLowerCase();
                    if (ext.equals("pfm")) {
                        outputFormat = 1;
                    }
                }
            }
            if (outputFormat == 1) {
                System.err.println("Decoded to pixels, writing PFM output.");
                writePFM(outputFilename, image);
            } else {
                System.err.println("Decoded to pixels, writing PNG output.");
                writePNG(outputFilename, image, outputDepth);
            }

        } else {
            System.err.println("Decoded to pixels, discarding output.");
        }
    }
}
