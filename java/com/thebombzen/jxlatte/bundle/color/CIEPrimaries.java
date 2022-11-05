package com.thebombzen.jxlatte.bundle.color;

public class CIEPrimaries {
    public final CIEXY red;
    public final CIEXY green;
    public final CIEXY blue;

    public CIEPrimaries(CIEXY red, CIEXY green, CIEXY blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public boolean matches(CIEPrimaries primaries) {
        return red.matches(primaries.red) && green.matches(primaries.green) && blue.matches(primaries.blue);
    }
}
