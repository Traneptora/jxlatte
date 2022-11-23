package com.thebombzen.jxlatte;

public class Options {

    public static final int OUTPUT_DEFAULT = -1;
    public static final int OUTPUT_PNG = 0;
    public static final int OUTPUT_PFM = 1;

    public static final int VERBOSITY_BASE = 0;
    public static final int VERBOSITY_INFO = 8;
    public static final int VERBOSITY_VERBOSE = 16;
    public static final int VERBOSITY_TRACE = 24;

    public static final int HDR_AUTO = -1;
    public static final int HDR_ON = 1;
    public static final int HDR_OFF = 2;

    public boolean debug = false;
    public int outputFormat = OUTPUT_DEFAULT;
    public int verbosity = VERBOSITY_BASE;
    public int hdr = HDR_AUTO;
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
