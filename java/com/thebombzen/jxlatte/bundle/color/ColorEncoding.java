package com.thebombzen.jxlatte.bundle.color;

public interface ColorEncoding {
    public static final int RGB = 0;
    public static final int GRAY = 1;
    public static final int XYB = 2;
    public static final int UNKNOWN = 3;

    public static boolean validate(int colorspace) {
        return colorspace >= 0 && colorspace <= 3;
    }
}
