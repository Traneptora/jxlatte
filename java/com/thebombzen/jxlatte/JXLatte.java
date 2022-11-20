package com.thebombzen.jxlatte;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.thebombzen.jxlatte.io.PFMWriter;
import com.thebombzen.jxlatte.io.PNGWriter;

public class JXLatte {

    public static final String JXLATTE_VERSION = "0.1.0";

    private static final int OUTPUT_DEFAULT = -1;
    private static final int OUTPUT_PNG = 0;
    private static final int OUTPUT_PFM = 1;

    public static final long FLAG_DEBUG = 1;
    public static final long FLAG_INFO_MASK = 6;
    public static final long FLAG_INFO = 2;
    public static final long FLAG_VERBOSE = 4;
    public static final long FLAG_TRACE = 6;
    public static final long FLAG_HDR = 8;

    private static void writePNG(String outputFilename, JXLImage image, int depth, long flags) throws IOException {
        boolean hdr = (flags & FLAG_HDR) > 0;
        int bitDepth = hdr ? 16 : depth;
        PNGWriter writer = new PNGWriter(image, bitDepth, hdr);
        try (OutputStream out = new BufferedOutputStream(outputFilename.equals("-")
                ? System.out : new FileOutputStream(outputFilename))) {
            writer.write(out);
        }
    }

    private static void writePFM(String outputFilename, JXLImage image) throws IOException {
        PFMWriter writer = new PFMWriter(image);
        try (OutputStream out = new BufferedOutputStream(outputFilename.equals("-") ? System.out : new FileOutputStream(outputFilename))) {
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
            "    --debug                      turn on debugging output",
            "    --output-png-depth=N         use N-bit output for PNG,",
            "                                     N must be 8 or 16",
            "    --output-format=<png|pfm>    write image in this output format",
            "    --info                       output info about the input file",
            "    --info=verbose               be more verbose with info",
            "    --hdr                        output PNG files in HDR",
            "                                     (BT.2020 Primaries, D65 White Point, PQ Transfer)",
            "",
            "If the output filename is not provided, jxlatte will discard the decoded pixels.",
        };
        System.err.println(String.join(String.format("%n"), lines));
        System.exit(success ? 0 : 1);
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            usage(false);
        }
        String inputFilename = null;
        String outputFilename = null;
        boolean foundMM = false;
        int outputDepth = -1;
        int outputFormat = OUTPUT_DEFAULT;
        long flags = 0;
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
                System.exit(1);
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
                        System.exit(1);
                    }
                    if (outputDepth != 8 && outputDepth != 16) {
                        System.err.println("jxlatte: only 8-bit and 16-bit outputs supported in PNG");
                        System.exit(1);
                    }
                    break;
                case "output-format":
                    switch (value.toLowerCase()) {
                        case "png":
                            outputFormat = OUTPUT_PNG;
                            break;
                        case "pfm":
                            outputFormat = OUTPUT_PFM;
                            break;
                        default:
                            System.err.format("jxlatte: unknown output format: %s%n", value);
                            System.exit(1);
                    }
                    break;
                case "debug":
                    flags = ~(~flags | FLAG_DEBUG);
                    switch (value.toLowerCase()) {
                        case "":
                        case "yes":
                            flags |= FLAG_DEBUG;
                            break;
                        case "no":
                            break;
                        default:
                            System.err.format("jxlatte: unknown debug flag: %s%n", value);
                            System.exit(1);
                    }
                    break;
                case "info":
                    flags = ~(~flags | FLAG_INFO_MASK);
                    switch (value.toLowerCase()) {
                        case "":
                        case "yes":
                            flags |= FLAG_INFO;
                            break;
                        case "no":
                            break;
                        case "verbose":
                            flags |= FLAG_VERBOSE;
                            break;
                        case "trace":
                            flags |= FLAG_TRACE;
                            break;
                        default:
                            System.err.format("jxlatte: unknown debug flag: %s%n", value);
                            System.exit(1);
                    }
                    break;
                case "hdr":
                    flags = ~(~flags | FLAG_HDR);
                    switch (value.toLowerCase()) {
                        case "":
                        case "yes":
                            flags |= FLAG_HDR;
                            break;
                        case "no":
                            break;
                        default:
                            System.err.format("jxlatte: unknown HDR flag: %s%n", value);
                            System.exit(1);
                    }
                    break;
                default:
                    System.err.format("jxlatte: unknown arg: %s%n", arg);
                    System.exit(1);
            }
        }

        if (inputFilename == null)
            usage(false);

        if (outputFilename != null && outputFormat == OUTPUT_DEFAULT) {
            int idx = outputFilename.lastIndexOf('.');
            if (idx >= 0) {
                String ext = outputFilename.substring(idx + 1).toLowerCase();
                if (ext.equals("pfm")) {
                    outputFormat = OUTPUT_PFM;
                } else if (ext.equals("png")) {
                    outputFormat = OUTPUT_PNG;
                }
            }
        }
        if (outputFilename != null && outputFormat == OUTPUT_DEFAULT) {
            System.err.println("jxlatte: Unable to determine output format from file extension.");
            System.exit(1);
        }

        JXLDecoder decoder = null;
        try {
            decoder = inputFilename.equals("-")
                ? new JXLDecoder(System.in, flags)
                : new JXLDecoder(inputFilename, flags);
        } catch (FileNotFoundException ex) {
            System.err.format("jxlatte: Unable to open file: %s%n", inputFilename);
            System.exit(2);
        }

        boolean debug = (flags & FLAG_DEBUG) > 0;

        JXLImage image = null;
        try {
            image = decoder.decode();
        } catch (EOFException | InvalidBitstreamException ex) {
            System.err.println("jxlatte: Invalid input bitstream");
            if (debug)
                ex.printStackTrace();
            System.exit(3);
        } catch (IOException ioe) {
            System.err.println("jxlatte: I/O error occurred");
            if (debug)
                ioe.printStackTrace();
            System.exit(2);
        } catch (UnsupportedOperationException uoe) {
            System.err.format("jxlatte: %s%n", uoe.getMessage());
            if (debug)
                uoe.printStackTrace();
            System.exit(4);
        } catch (Exception re) {
            System.err.println("jxlatte: BUG: " + re.getMessage());
            re.printStackTrace();
            System.exit(4);
        }

        if (outputFilename != null) {
            try {
                if (outputFormat == OUTPUT_PFM) {
                    System.err.println("Decoded to pixels, writing PFM output.");
                    writePFM(outputFilename, image);
                } else if (outputFormat == OUTPUT_PNG) {
                    System.err.println("Decoded to pixels, writing PNG output.");
                    writePNG(outputFilename, image, outputDepth, flags);
                }
            } catch (FileNotFoundException fnfe) {
                System.err.println("jxlatte: could not open output file for writing");
                if (debug)
                    fnfe.printStackTrace();
                System.exit(2);
            } catch (IOException ioe) {
                System.err.println("jxlatte: error writing output file");
                if (debug)
                    ioe.printStackTrace();
                System.exit(2);
            }
        } else {
            System.err.println("Decoded to pixels, discarding output.");
        }
    }
}
