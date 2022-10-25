package com.thebombzen.jxlatte.bundle.color;

public interface WhitePoint {
    public static final int D65 = 1;
    public static final int CUSTOM = 2;
    public static final int E = 10;
    public static final int DCI = 11;

    public static boolean validate(int whitePoint) {
        return whitePoint == D65
            || whitePoint == CUSTOM
            || whitePoint == E
            || whitePoint == DCI;
    }

    public static CIEXY getWhitePoint(int whitePoint) {
        switch (whitePoint) {
            case D65:
                return new CIEXY(0.3127f, 0.3290f);
            case E:
                return new CIEXY(1f/3f, 1f/3f);
            case DCI:
                return new CIEXY(0.314f, 0.351f);
        }
        return null;
    }
}
