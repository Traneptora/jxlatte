package com.thebombzen.jxlatte.frame.modular;

import java.util.Objects;

public class ModularChannelInfo {
    public int width;
    public int height;
    public int hshift;
    public int vshift;
    public int x0;
    public int y0;
    public boolean forceWP;

    public ModularChannelInfo() {
        this(0, 0, 0, 0, 0, 0, false);
    }

    public ModularChannelInfo(int width, int height, int hshift, int vshift) {
        this(width, height, hshift, vshift, 0, 0, false);
    }

    public ModularChannelInfo(int width, int height, int hshift, int vshift, int x0, int y0, boolean forceWP) {
        this.width = width;
        this.height = height;
        this.hshift = hshift;
        this.vshift = vshift;
        this.x0 = x0;
        this.y0 = y0;
        this.forceWP = forceWP;
    }

    public ModularChannelInfo(ModularChannelInfo info) {
        this(info.width, info.height, info.hshift, info.vshift, info.x0, info.y0, info.forceWP);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, hshift, vshift, x0, y0, forceWP);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ModularChannelInfo other = (ModularChannelInfo) obj;
        return width == other.width && height == other.height && hshift == other.hshift && vshift == other.vshift
                && x0 == other.x0 && y0 == other.y0 && forceWP == other.forceWP;
    }
}
