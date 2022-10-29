package com.thebombzen.jxlatte.bundle.color;

import java.util.Objects;

public class CIEXY {
    public final float x;
    public final float y;

    public CIEXY(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public CIEXY(CIEXY xy) {
        this(xy.x, xy.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CIEXY other = (CIEXY) obj;
        return Float.floatToIntBits(x) == Float.floatToIntBits(other.x)
                && Float.floatToIntBits(y) == Float.floatToIntBits(other.y);
    }

    @Override
    public String toString() {
        return "CIEXY [x=" + x + ", y=" + y + "]";
    }
    
}
