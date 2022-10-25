package com.thebombzen.jxlatte.bundle.color;

public interface RenderingIntent {
    public static final int PERCEPTUAL = 0;
    public static final int RELATIVE = 1;
    public static final int SATURATION = 2;
    public static final int ABSOLUTE = 3;

    public static boolean validate(int renderingIntent) {
        return renderingIntent >= 0 && renderingIntent <= 3;
    }
}
