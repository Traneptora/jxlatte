package com.thebombzen.jxlatte.bundle.color;

public interface Primaries {
    public static final int SRGB = 1;
    public static final int CUSTOM = 2;
    public static final int BT2100 = 9;
    public static final int P3 = 11;

    public static boolean validate(int primaries) {
        return primaries == SRGB
            || primaries == CUSTOM
            || primaries == BT2100
            || primaries == P3;
    }

    public static CIEPrimaries getPrimaries(int primaries) {
        switch (primaries) {
            case SRGB:
                return new CIEPrimaries(new CIEXY(0.639998686f, 0.330010138f),
                    new CIEXY(0.300003784f, 0.600003357f),
                    new CIEXY(0.150002046f, 0.059997204f));
            case BT2100:
                return new CIEPrimaries(new CIEXY(0.708f, 0.292f),
                    new CIEXY(0.170f, 0.797f),
                    new CIEXY(0.131f, 0.046f));
            case P3:
                return new CIEPrimaries(
                    new CIEXY(0.680f, 0.320f),
                    new CIEXY(0.265f, 0.690f),
                    new CIEXY(0.150f, 0.060f));
        }
        return null;
    }
}
