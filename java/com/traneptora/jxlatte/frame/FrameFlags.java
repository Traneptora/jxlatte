package com.traneptora.jxlatte.frame;

public interface FrameFlags {
    public static final int REGULAR_FRAME = 0;
    public static final int LF_FRAME = 1;
    public static final int REFERENCE_ONLY = 2;
    public static final int SKIP_PROGRESSIVE = 3;

    public static final int VARDCT = 0;
    public static final int MODULAR = 1;

    public static final int NOISE = 1 << 0;
    public static final int PATCHES = 1 << 1;
    public static final int SPLINES = 1 << 4;
    public static final int USE_LF_FRAME = 1 << 5;
    public static final int SKIP_ADAPTIVE_LF_SMOOTHING = 1 << 7;

    public static final int BLEND_REPLACE = 0;
    public static final int BLEND_ADD = 1;
    public static final int BLEND_BLEND = 2;
    public static final int BLEND_MULADD = 3;
    public static final int BLEND_MULT = 4;

    public static String getFrameTypeName(int frameType) {
        switch (frameType) {
            case REGULAR_FRAME:
                return "Regular Frame";
            case LF_FRAME:
                return "LF Frame";
            case REFERENCE_ONLY:
                return "Reference Only";
            case SKIP_PROGRESSIVE:
                return "Skip Progressive";
            default:
                return "Unknown";
        }
    }

    public static String getFrameEncodingName(int frameEncoding) {
        switch (frameEncoding) {
            case VARDCT:
                return "VarDCT";
            case MODULAR:
                return "Modular";
            default:
                return "Unknown";
        }
    }

    public static String getBlendModeName(int blendMode) {
        switch (blendMode) {
            case BLEND_REPLACE:
                return "Replace";
            case BLEND_ADD:
                return "Add";
            case BLEND_BLEND:
                return "Blend";
            case BLEND_MULADD:
                return "Muladd";
            case BLEND_MULT:
                return "Mult";
            default:
                return "Unknown";
        }
    }
}
