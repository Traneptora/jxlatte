package com.thebombzen.jxlatte.frame.vardct;

public enum TransformType {
    DCT8("DCT 8x8", 0, 1, 1, 8, 8),
    HORNUSS("Hornuss", 1, 1, 1, 8, 8),
    DCT2("DCT 2x2", 2, 1, 1, 2, 2),
    DCT4("DCT 4x4", 3, 1, 1, 4, 4),
    DCT16("DCT 16x16", 4, 2, 2, 16, 16),
    DCT32("DCT 32x32", 5, 4, 4, 32, 32),
    DCT16_8("DCT 16x8", 6, 2, 1, 16, 8),
    DCT8_16("DCT 8x16", 7, 1, 2, 8, 16),
    DCT32_8("DCT 32x8", 8, 4, 1, 32, 8),
    DCT8_32("DCT 8x32", 9, 1, 4, 8, 32),
    DCT32_16("DCT 32x16", 10, 4, 2, 32, 16),
    DCT16_32("DCT 16x32", 11, 2, 4, 16, 32),
    DCT4_8("DCT 4x8", 12, 1, 1, 4, 8),
    DCT8_4("DCT 8x4", 13, 1, 1, 8, 4),
    AFV0("AFV0", 14, 1, 1, 1, 1),
    AFV1("AFV1", 15, 1, 1, 2, 2),
    AFV2("AFV2", 16, 1, 1, 4, 4),
    AFV3("AFV3", 17, 1, 1, 8, 8),
    DCT64("DCT 64x64", 18, 8, 8, 64, 64),
    DCT64_32("DCT 64x32", 19, 8, 4, 64, 32),
    DCT32_64("DCT 32x64", 20, 4, 8, 32, 64),
    DCT128("DCT 128x128", 21, 16, 16, 128, 128),
    DCT128_64("DCT 128x64", 22, 16, 8, 128, 64),
    DCT64_128("DCT 64x128", 23, 8, 16, 64, 128),
    DCT256("DCT 256x256", 24, 32, 32, 256, 256),
    DCT256_128("DCT 256x128", 25, 32, 16, 256, 128),
    DCT128_256("DCT 128x256", 26, 16, 32, 128, 256);

    public static TransformType get(int type) {
        return TransformType.values()[type];
    }

    public final String name;
    public final int type;
    public final int dctSelectWidth;
    public final int dctSelectHeight;
    public final int blockWidth;
    public final int blockHeight;

    private TransformType(String name, int type, int dctSelectWidth, int dctSelectHeight,
            int blockWidth, int blockHeight) {
        this.name = name;
        this.type = type;
        this.dctSelectWidth = dctSelectWidth;
        this.dctSelectHeight = dctSelectHeight;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
    }
}
