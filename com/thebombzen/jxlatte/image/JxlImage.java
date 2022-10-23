package com.thebombzen.jxlatte.image;

public class JxlImage {
    private JxlImageFormat imageFormat;
    private byte[] pixelData = null;
    private int[] strides = null;
    private int[] offsets = null;
    private int width;
    private int height;
    public JxlImage(JxlImageFormat format, int width, int height) {
        this.imageFormat = format;
        int size = 0;
        int channels = format.getNumChannels();
        strides = new int[channels];
        offsets = new int[channels];
        for (int i = 0; i < channels; i++) {
            JxlChannelType type = format.getChannelType(i);
            strides[i] = format.getRowStride();
            if (strides[i] <= 0) 
                strides[i] = width * type.width;
            offsets[i] = size;
            size += strides[i] * height;
            
        }
        pixelData = new byte[size];
        this.width = width;
        this.height = height;
    }

    public JxlImageFormat getFormat() {
        return this.imageFormat;
    }

    public JxlChannelType getChannelType(int channel) {
        return this.imageFormat.getChannelType(channel);
    }

    public byte[] getPixelBuffer() {
        return pixelData;
    }

    public int getChannelOffset(int channel) {
        return offsets[channel];
    }

    public int getChannelLength(int channel) {
        if (channel == offsets.length - 1) {
            return pixelData.length - offsets[offsets.length - 1]; 
        } else {
            return offsets[channel + 1] - offsets[channel];
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
