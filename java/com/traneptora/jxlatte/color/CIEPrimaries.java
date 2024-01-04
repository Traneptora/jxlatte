package com.thebombzen.jxlatte.color;

import java.util.Objects;

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

    public static boolean matches(CIEPrimaries a, CIEPrimaries b) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        return a.matches(b);
    }

    @Override
    public String toString() {
        return String.format("CIEPrimaries [red=%s, green=%s, blue=%s]", red, green, blue);
    }

    @Override
    public boolean equals(Object another) {
        if (another == null || !another.getClass().equals(this.getClass()))
            return false;
        CIEPrimaries other = (CIEPrimaries)another;
        return Objects.equals(red, other.red) && Objects.equals(green, other.green) &&
               Objects.equals(blue, other.blue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(red, green, blue);
    }
}
