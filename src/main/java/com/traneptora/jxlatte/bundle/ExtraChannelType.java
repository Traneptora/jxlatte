package com.traneptora.jxlatte.bundle;

public interface ExtraChannelType {
    
    public static final int ALPHA = 0;
    public static final int DEPTH = 1;
    public static final int SPOT_COLOR = 2;
    public static final int SELECTION_MASK = 3;
    public static final int CMYK_BLACK = 4;
    public static final int COLOR_FILTER_ARRAY = 5;
    public static final int THERMAL = 6;
    public static final int NON_OPTIONAL = 15;
    public static final int OPTIONAL = 16;

    public static boolean validate(int ec) {
        return ec >= 0 && ec <= 6 || ec == 15 || ec == 16;
    }
}
