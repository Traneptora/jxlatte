package com.thebombzen.jxlatte.image;

import java.util.Arrays;

public class JxlImageFormat {
    private int bit_depth;
    private int num_channels;
    private int row_stride;
    private JxlChannelType[] channel_types;

    public JxlImageFormat(int bit_depth, int row_stride, JxlChannelType... channelTypes) {
        if (bit_depth != 8 && bit_depth != 16 && bit_depth != 32)
            throw new IllegalArgumentException("Only 8, 16, and 32 supported.");
        if (channelTypes.length == 0)
            throw new IllegalArgumentException("Must have at least one channel.");
        this.bit_depth = bit_depth;
        this.num_channels = channelTypes.length;
        this.channel_types = Arrays.copyOf(channelTypes, num_channels);
        this.row_stride = row_stride;
    }

    public int getNumChannels() {
        return this.num_channels;
    }

    public JxlChannelType getChannelType(int index) {
        return this.channel_types[index];
    }

    public int getBitDepth() {
        return bit_depth;
    }

    public int getRowStride() {
        return row_stride;
    }
}
