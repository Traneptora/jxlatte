package com.thebombzen.jxlatte.bundle.color;

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

    @Override
    public int hashCode() {
        return Objects.hash(red, green, blue);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CIEPrimaries other = (CIEPrimaries) obj;
        return Objects.equals(red, other.red) && Objects.equals(green, other.green) && Objects.equals(blue, other.blue);
    }

    @Override
    public String toString() {
        return "CIEPrimaries [red=" + red + ", green=" + green + ", blue=" + blue + "]";
    }
}
