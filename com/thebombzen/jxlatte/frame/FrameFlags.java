package com.thebombzen.jxlatte.frame;

public interface FrameFlags {
    public static final int REGULAR_FRAME = 0;
    public static final int LF_FRAME = 1;
    public static final int REFERENCE_ONLY = 2;
    public static final int SKIP_PROGRESSIVE = 3;

    public static final int VARDCT = 0;
    public static final int MODULAR = 1;

    public static final int NOISE = 1;
    public static final int PATCHES = 2;
    public static final int SPLINES = 16;
    public static final int USE_LF_FRAME = 32;
    public static final int SKIP_ADAPTIVE_LF_SMOOTHING = 128;

    public static final int BLEND_REPLACE = 0;
    public static final int BLEND_ADD = 1;
    public static final int BLEND_BLEND = 2;
    public static final int BLEND_MULADD = 3;
    public static final int BLEND_MULT = 4;
}
