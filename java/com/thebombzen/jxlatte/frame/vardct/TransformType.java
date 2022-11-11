package com.thebombzen.jxlatte.frame.vardct;

import com.thebombzen.jxlatte.InvalidBitstreamException;

public enum TransformType {
    DCT8("DCT 8x8", 0, 0, 1, 1, 8, 8, 8, 8),
    HORNUSS("Hornuss", 1, 1, 1, 1, 8, 8, 8, 8),
    DCT2("DCT 2x2", 2, 2, 1, 1, 2, 2, 8, 8),
    DCT4("DCT 4x4", 3, 3, 1, 1, 4, 4, 8, 8),
    DCT16("DCT 16x16", 4, 4, 2, 2, 16, 16, 16, 16),
    DCT32("DCT 32x32", 5, 5, 4, 4, 32, 32, 32, 32),
    DCT16_8("DCT 16x8", 6, 6, 2, 1, 16, 8, 8, 16),
    DCT8_16("DCT 8x16", 7, 6, 1, 2, 8, 16, 8, 16),
    DCT32_8("DCT 32x8", 8, 7, 4, 1, 32, 8, 8, 32),
    DCT8_32("DCT 8x32", 9, 7, 1, 4, 8, 32, 8, 32),
    DCT32_16("DCT 32x16", 10, 8, 4, 2, 32, 16, 16, 32),
    DCT16_32("DCT 16x32", 11, 8, 2, 4, 16, 32, 16, 32),
    DCT4_8("DCT 4x8", 12, 9, 1, 1, 4, 8, 8, 8),
    DCT8_4("DCT 8x4", 13, 9, 1, 1, 8, 4, 8, 8),
    AFV0("AFV0", 14, 10, 1, 1, 1, 1, 8, 8),
    AFV1("AFV1", 15, 10, 1, 1, 2, 2, 8, 8),
    AFV2("AFV2", 16, 10, 1, 1, 4, 4, 8, 8),
    AFV3("AFV3", 17, 10, 1, 1, 8, 8, 8, 8),
    DCT64("DCT 64x64", 18, 11, 8, 8, 64, 64, 64, 64),
    DCT64_32("DCT 64x32", 19, 12, 8, 4, 64, 32, 32, 64),
    DCT32_64("DCT 32x64", 20, 12, 4, 8, 32, 64, 32, 64),
    DCT128("DCT 128x128", 21, 13, 16, 16, 128, 128, 128, 128),
    DCT128_64("DCT 128x64", 22, 14, 16, 8, 128, 64, 64, 128),
    DCT64_128("DCT 64x128", 23, 14, 8, 16, 64, 128, 64, 128),
    DCT256("DCT 256x256", 24, 15, 32, 32, 256, 256, 256, 256),
    DCT256_128("DCT 256x128", 25, 16, 32, 16, 256, 128, 128, 256),
    DCT128_256("DCT 128x256", 26, 16, 16, 32, 128, 256, 128, 256);

    public static final int MODE_LIBRARY = 0;
    public static final int MODE_HORNUSS = 1;
    public static final int MODE_DCT2 = 2;
    public static final int MODE_DCT4 = 3;
    public static final int MODE_DCT4_8 = 4;
    public static final int MODE_AFV = 5;
    public static final int MODE_DCT = 6;
    public static final int MODE_RAW = 7;

    public static boolean validateIndex(int index, int mode) throws InvalidBitstreamException {
        if (mode < 0 || mode > 7)
            throw new IllegalArgumentException();
        if (mode == MODE_LIBRARY || mode == MODE_DCT || mode == MODE_RAW)
            return true;
        if (index >= 0 && index <= 3 || index == 9 || index == 10)
            return true;
        throw new InvalidBitstreamException("Invalid index for mode: " + index + ", " + mode);
    }

    public static TransformType get(int type) {
        return TransformType.values()[type];
    }

    public final String name;
    public final int type;
    public final int parameterIndex;
    public final int dctSelectWidth;
    public final int dctSelectHeight;
    public final int blockWidth;
    public final int blockHeight;
    public final int matrixWidth;
    public final int matrixHeight;

    private TransformType(String name, int type, int parameterIndex, int dctSelectWidth, int dctSelectHeight,
            int blockWidth, int blockHeight, int matrixWidth, int matrixHeight) {
        this.name = name;
        this.type = type;
        this.parameterIndex = parameterIndex;
        this.dctSelectWidth = dctSelectWidth;
        this.dctSelectHeight = dctSelectHeight;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.matrixWidth = matrixWidth;
        this.matrixHeight = matrixHeight;
    }
}
