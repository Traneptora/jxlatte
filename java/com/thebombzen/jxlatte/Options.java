package com.thebombzen.jxlatte;

public class Options {

    public static final int OUTPUT_DEFAULT = -1;
    public static final int OUTPUT_PNG = 0;
    public static final int OUTPUT_PFM = 1;

    public static final int VERBOSITY_BASE = 0;
    public static final int VERBOSITY_INFO = 8;
    public static final int VERBOSITY_VERBOSE = 16;
    public static final int VERBOSITY_TRACE = 24;

    public boolean debug = false;
    public int outputFormat = -1;
    public int verbosity = 0;
    public boolean hdr = false;
    public int outputDepth = -1;
    public int outputCompression = 0;

    public String input = null;
    public String output = null;

    public Options() {

    }

    public Options(Options options) {
        this.debug = options.debug;
        this.outputFormat = options.outputFormat;
        this.verbosity = options.verbosity;
        this.hdr = options.hdr;
        this.outputDepth = options.outputDepth;
        this.outputCompression = options.outputCompression;

        this.input = options.input;
        this.output = options.output;
    }
}
