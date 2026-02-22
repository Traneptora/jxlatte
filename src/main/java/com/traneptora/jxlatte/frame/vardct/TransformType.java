package com.traneptora.jxlatte.frame.vardct;

import java.util.stream.Stream;

import com.traneptora.jxlatte.io.InvalidBitstreamException;
import com.traneptora.jxlatte.util.Dimension;
import com.traneptora.jxlatte.util.MathHelper;

public enum TransformType {
    DCT8("DCT 8x8", 0, 0, 0, 0, 8, 8),
    HORNUSS("Hornuss", 1, 1, 1, 3, 8, 8),
    DCT2("DCT 2x2", 2, 2, 1, 1, 8, 8),
    DCT4("DCT 4x4", 3, 3, 1, 2, 8, 8),
    DCT16("DCT 16x16", 4, 4, 2, 0, 16, 16),
    DCT32("DCT 32x32", 5, 5, 3, 0, 32, 32),
    DCT16_8("DCT 16x8", 6, 6, 4, 0, 16, 8),
    DCT8_16("DCT 8x16", 7, 6, 4, 0, 8, 16),
    DCT32_8("DCT 32x8", 8, 7, 5, 0, 32, 8),
    DCT8_32("DCT 8x32", 9, 7, 5, 0, 8, 32),
    DCT32_16("DCT 32x16", 10, 8, 6, 0, 32, 16),
    DCT16_32("DCT 16x32", 11, 8, 6, 0, 16, 32),
    DCT4_8("DCT 4x8", 12, 9, 1, 5, 8, 8),
    DCT8_4("DCT 8x4", 13, 9, 1, 4, 8, 8),
    AFV0("AFV0", 14, 10, 1, 6, 8, 8),
    AFV1("AFV1", 15, 10, 1, 6, 8, 8),
    AFV2("AFV2", 16, 10, 1, 6, 8, 8),
    AFV3("AFV3", 17, 10, 1, 6, 8, 8),
    DCT64("DCT 64x64", 18, 11, 7, 0, 64, 64),
    DCT64_32("DCT 64x32", 19, 12, 8, 0, 64, 32),
    DCT32_64("DCT 32x64", 20, 12, 8, 0, 32, 64),
    DCT128("DCT 128x128", 21, 13, 9, 0, 128, 128),
    DCT128_64("DCT 128x64", 22, 14, 10, 0, 128, 64),
    DCT64_128("DCT 64x128", 23, 14, 10, 0, 64, 128),
    DCT256("DCT 256x256", 24, 15, 11, 0, 256, 256),
    DCT256_128("DCT 256x128", 25, 16, 12, 0, 256, 128),
    DCT128_256("DCT 128x256", 26, 16, 12, 0, 128, 256);

    public static final int MODE_LIBRARY = 0;
    public static final int MODE_HORNUSS = 1;
    public static final int MODE_DCT2 = 2;
    public static final int MODE_DCT4 = 3;
    public static final int MODE_DCT4_8 = 4;
    public static final int MODE_AFV = 5;
    public static final int MODE_DCT = 6;
    public static final int MODE_RAW = 7;

    public static final int METHOD_DCT = 0;
    public static final int METHOD_DCT2 = 1;
    public static final int METHOD_DCT4 = 2;
    public static final int METHOD_HORNUSS = 3;
    public static final int METHOD_DCT8_4 = 4;
    public static final int METHOD_DCT4_8 = 5;
    public static final int METHOD_AFV = 6;

    private static TransformType[] typeLookup = new TransformType[27];
    private static TransformType[] parameterLookup = new TransformType[17];
    private static TransformType[] orderLookup = new TransformType[13];

    public static boolean validateIndex(int index, int mode) throws InvalidBitstreamException {
        if (mode < 0 || mode > 7)
            throw new IllegalArgumentException();
        if (mode == MODE_LIBRARY || mode == MODE_DCT || mode == MODE_RAW)
            return true;
        if (index >= 0 && index <= 3 || index == 9 || index == 10)
            return true;
        throw new InvalidBitstreamException("Invalid index for mode: " + index + ", " + mode);
    }

    private static TransformType filterByParamterIndex(int parameterIndex) {
        return Stream.of(TransformType.values()).filter(
            t -> t.parameterIndex == parameterIndex && !t.isVertical()).findAny().get();
    }

    private static TransformType filterByOrderID(int orderID) {
        return Stream.of(TransformType.values()).filter(
            t -> t.orderID == orderID && !t.isVertical()).findAny().get();
    }

    private static TransformType filterByType(int typeIndex) {
        return Stream.of(TransformType.values()).filter(
            t -> t.type == typeIndex).findAny().get();
    }

    static {
        for (int i = 0; i < 27; i++) {
            typeLookup[i] = filterByType(i);
        }
        for (int i = 0; i < 17; i++) {
            parameterLookup[i] = filterByParamterIndex(i);
        }
        for (int i = 0; i < 13; i++) {
            orderLookup[i] = filterByOrderID(i);
        }
    }

    public static TransformType getByType(int typeIndex) {
        return typeLookup[typeIndex];
    }

    public static TransformType getByParameterIndex(int parameterIndex) {
        return parameterLookup[parameterIndex];
    }

    public static TransformType getByOrderID(int orderID) {
        return orderLookup[orderID];
    }

    public final String name;
    public final int type;
    public final int parameterIndex;
    public final int dctSelectHeight;
    public final int dctSelectWidth;
    public final int pixelHeight;
    public final int pixelWidth;
    public final int matrixHeight;
    public final int matrixWidth;
    public final int orderID;
    public final int transformMethod;
    public final float[][] llfScale;

    public boolean isVertical() {
        return pixelHeight > pixelWidth;
    }

    public String toString() {
        return this.name;
    }

    public boolean flip() {
        return pixelHeight > pixelWidth || transformMethod == METHOD_DCT && pixelHeight == pixelWidth;
    }

    public Dimension getPixelSize() {
        return new Dimension(pixelHeight, pixelWidth);
    }

    public Dimension getMatrixSize() {
        return new Dimension(matrixHeight, matrixWidth);
    }

    public Dimension getDctSelectSize() {
        return new Dimension(dctSelectHeight, dctSelectWidth);
    }

    private TransformType(String name, int type, int parameterIndex, int orderID, int transformMethod,
            int pixelHeight, int pixelWidth) {
        this.name = name;
        this.type = type;
        this.parameterIndex = parameterIndex;
        this.pixelHeight = pixelHeight;
        this.pixelWidth = pixelWidth;
        this.dctSelectHeight = pixelHeight >> 3;
        this.dctSelectWidth = pixelWidth >> 3;
        this.matrixHeight = Math.min(pixelHeight, pixelWidth);
        this.matrixWidth = Math.max(pixelHeight, pixelWidth);       
        this.orderID = orderID;
        this.transformMethod = transformMethod;
        this.llfScale = new float[dctSelectHeight][dctSelectWidth];
        int yll = MathHelper.ceilLog2(dctSelectHeight);
        int xll = MathHelper.ceilLog2(dctSelectWidth);
        for (int y = 0; y < dctSelectHeight; y++) {
            for (int x = 0; x < dctSelectWidth; x++) {
                llfScale[y][x] = LLFScale.scaleF(y, yll) * LLFScale.scaleF(x, xll);
            }
        }
    }
}
