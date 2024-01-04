package com.thebombzen.jxlatte.frame.modular;

import com.thebombzen.jxlatte.util.IntPoint;

public class ModularChannelInfo {
    public int width;
    public int height;
    public int hshift;
    public int vshift;
    public IntPoint origin;
    public boolean forceWP;

    public ModularChannelInfo() {
        this(0, 0, 0, 0);
    }

    public ModularChannelInfo(int width, int height, int hshift, int vshift) {
        this(width, height, hshift, vshift, new IntPoint(), false);
    }

    public ModularChannelInfo(int width, int height, int hshift, int vshift, IntPoint origin, boolean forceWP) {
        this.width = width;
        this.height = height;
        this.hshift = hshift;
        this.vshift = vshift;
        this.origin = new IntPoint(origin);
        this.forceWP = forceWP;
    }

    public ModularChannelInfo(ModularChannelInfo info) {
        this(info.width, info.height, info.hshift, info.vshift, info.origin, info.forceWP);
    }

    @Override
    public String toString() {
        return String.format("ModularChannelInfo [width=%s, height=%s, hshift=%s, vshift=%s, origin=%s, forceWP=%s]",
                width, height, hshift, vshift, origin, forceWP);
    }
}
