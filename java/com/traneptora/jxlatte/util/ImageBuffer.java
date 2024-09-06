package com.traneptora.jxlatte.util;

public class ImageBuffer {

    public static final int TYPE_INT = 0;
    public static final int TYPE_FLOAT = 1;

    private int type;
    public final int height;
    public final int width;

    private Object[] buffer;

    public ImageBuffer(ImageBuffer buffer) {
        this(buffer, true);
    }

    public ImageBuffer(ImageBuffer buffer, boolean copyData) {
        this.type = buffer.type;
        this.height = buffer.height;
        this.width = buffer.width;
        if (this.type == TYPE_INT) {
            this.buffer = new int[height][width];
        } else {
            this.buffer = new float[height][width];
        }
        if (copyData) {
            for (int y = 0; y < height; y++) {
                System.arraycopy(buffer.buffer[y], 0, this.buffer[y], 0, width);
            }
        }
    }

    /* used for System.arraycopy */
    public Object[] getBackingBuffer() {
        return buffer;
    }

    public ImageBuffer(float[][] buffer) {
        this.type = TYPE_FLOAT;
        this.height = buffer.length;
        this.width = buffer[0].length;
        this.buffer = buffer;
    }

    public ImageBuffer(int[][] buffer) {
        this.type = TYPE_INT;
        this.height = buffer.length;
        this.width = buffer[0].length;
        this.buffer = buffer;
    }

    public ImageBuffer(int type, int height, int width) {
        if (type != TYPE_INT && type != TYPE_FLOAT)
            throw new IllegalArgumentException();
        if (height < 0 || height > (1 << 30) || width < 0 || width > (1 << 30))
            throw new IllegalArgumentException();

        this.type = type;
        this.height =  height;
        this.width = width;

        if (type == TYPE_INT) {
            buffer = new int[height][width];
        } else {
            buffer = new float[height][width];
        }
    }

    public float[][] getFloatBuffer() {
        if (type != TYPE_FLOAT)
            throw new IllegalStateException("This is not a float buffer");
        return (float[][]) buffer;
    }

    public int[][] getIntBuffer() {
        if (type != TYPE_INT)
            throw new IllegalStateException("This is not an int buffer");
        return (int[][]) buffer;
    }

    public boolean isInt() {
        return type == TYPE_INT;
    }

    public boolean isFloat() {
        return type == TYPE_FLOAT;
    }

    public int getType() {
        return type;
    }

    public void castToFloatIfInt(int maxValue) {
        if (isInt())
            castToFloatBuffer(maxValue);
    }

    public void castToIntIfFloat(int maxValue) {
        if (isFloat())
            castToIntBuffer(maxValue);
    }

    public void castToFloatBuffer(int maxValue) {
        if (type == TYPE_FLOAT)
            throw new IllegalStateException("This is already a float buffer");
        if (maxValue < 1)
            throw new IllegalArgumentException("invalid Max Value");
        int[][] oldBuffer = (int[][]) buffer;
        float[][] newBuffer = new float[height][width];
        float scaleFactor = 1.0f / maxValue;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                newBuffer[y][x] = oldBuffer[y][x] * scaleFactor;
            }
        }
        this.buffer = newBuffer;
        this.type = TYPE_FLOAT;
    }

    public void castToIntBuffer(int maxValue) {
        if (type == TYPE_INT)
            throw new IllegalStateException("This is already an int buffer");
        if (maxValue < 1)
            throw new IllegalArgumentException("invalid Max Value");
        float[][] oldBuffer = (float[][]) buffer;
        int[][] newBuffer = new int[height][width];
        float scaleFactor = maxValue;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v = (int)(oldBuffer[y][x] * scaleFactor + 0.5f);
                newBuffer[y][x] = v < 0 ? 0 : v > maxValue ? maxValue : v;
            }
        }
        this.buffer = newBuffer;
        this.type = TYPE_INT;
    }

    /**
     * Clamp an int buffer
     */
    public void clamp(int maxValue) {
        if (type == TYPE_FLOAT)
            throw new IllegalArgumentException("This is a float buffer");
        int[][] buf = (int[][])buffer;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v = buf[y][x];
                buf[y][x] = v < 0 ? 0 : v > maxValue ? maxValue : v;
            }
        }
    }

    /**
     * Clamp a float buffer
     */
    public void clamp() {
        if (type == TYPE_INT)
            throw new IllegalArgumentException("This is an int buffer");
        float[][] buf = (float[][])buffer;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float v = buf[y][x];
                buf[y][x] = v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v;
            }
        }
    }

    @Override
    public String toString() {
        return String.format("ImageBuffer [type=%s, height=%s, width=%s]", type, height, width);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    public boolean equals(Object another) {
        return this == another;
    }
}
