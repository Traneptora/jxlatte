package com.thebombzen.jxlatte.image;

public enum ChannelType {
    PACKED_RGB(3),
    PACKED_RGBA(4),
    PACKED_GRAYA(2),
    GRAY(1),
    RED(1),
    GREEN(1),
    BLUE(1),
    ALPHA(1),
    EXTRA(1);

    public final int width;

    private ChannelType(int width) {
        this.width = width;
    }
}
