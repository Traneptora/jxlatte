package com.traneptora.jxlatte.color;

import java.awt.color.ColorSpace;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

import com.traneptora.jxlatte.util.MathHelper;

public class JXLColorSpace extends ColorSpace {
    public final CIEPrimaries primaries;
    public final CIEXY whitePoint;
    public final int transfer;
    public final DoubleUnaryOperator transferFunction;
    public final DoubleUnaryOperator inverseTransferFunction;
    private float[][] primariesToXYZD50;
    private float[][] primariesFromXYZD50;
    private float[][] primariesToSRGB;
    private float[][] primariesFromSRGB;

    public JXLColorSpace(CIEPrimaries primaries, CIEXY whitePoint, int transfer, int numComponents) {
        super(Objects.hash(primaries, whitePoint, transfer), numComponents);
        this.primaries = primaries;
        this.whitePoint = whitePoint;
        this.transfer = transfer;
        this.primariesToXYZD50 = ColorManagement.primariesToXYZD50(primaries, whitePoint);
        this.primariesFromXYZD50 = MathHelper.invertMatrix3x3(primariesToXYZD50);
        this.primariesToSRGB = ColorManagement.getConversionMatrix(
            ColorManagement.PRI_SRGB, ColorManagement.WP_D65, primaries, whitePoint);
        this.primariesFromSRGB = MathHelper.invertMatrix3x3(primariesToSRGB);
        this.transferFunction = ColorManagement.getTransferFunction(transfer);
        this.inverseTransferFunction = ColorManagement.getInverseTransferFunction(transfer);
    }

    public JXLColorSpace(int primaries, int whitePoint, int transfer) {
        this(ColorFlags.getPrimaries(primaries), ColorFlags.getWhitePoint(whitePoint), transfer, 3);
    }

    private float[] transfer(float[] thisSpace) {
        thisSpace[1] = (float)transferFunction.applyAsDouble(thisSpace[1]);
        if (getNumComponents() < 3) {
            thisSpace[0] = thisSpace[1];
        } else {
            thisSpace[0] = (float)transferFunction.applyAsDouble(thisSpace[0]);
            thisSpace[2] = (float)transferFunction.applyAsDouble(thisSpace[2]);
        }
        return thisSpace;
    }

    private float[] linearize(float[] colorvalue) {
        float[] linear = new float[3];
        for (int i = 0; i < getNumComponents(); i++)
            linear[i] = (float)inverseTransferFunction.applyAsDouble(colorvalue[i]);
        if (getNumComponents() < 3)
            linear[1] = linear[2] = linear[0];
        return linear;
    }

    @Override
    public float[] toRGB(float[] colorvalue) {
        float[] linear = linearize(colorvalue);
        float[] sRGB = MathHelper.matrixMutliply(this.primariesToSRGB, linear);
        DoubleUnaryOperator sRGBTransfer = ColorManagement.getTransferFunction(ColorFlags.TF_SRGB);
        // clamp here because BufferedImage forbids out of gamut colors
        sRGB[0] = MathHelper.clamp((float)sRGBTransfer.applyAsDouble(sRGB[0]), 0.0f, 1.0f);
        sRGB[1] = MathHelper.clamp((float)sRGBTransfer.applyAsDouble(sRGB[1]), 0.0f, 1.0f);
        sRGB[2] = MathHelper.clamp((float)sRGBTransfer.applyAsDouble(sRGB[2]), 0.0f, 1.0f);
        return sRGB;
    }

    @Override
    public float[] fromRGB(float[] rgbvalue) {
        float[] linear = new float[3];
        DoubleUnaryOperator inverseSRGB = ColorManagement.getInverseTransferFunction(ColorFlags.TF_SRGB);
        linear[0] = (float)inverseSRGB.applyAsDouble(rgbvalue[0]);
        linear[1] = (float)inverseSRGB.applyAsDouble(rgbvalue[1]);
        linear[2] = (float)inverseSRGB.applyAsDouble(rgbvalue[2]);
        float[] thisSpace = MathHelper.matrixMutliply(this.primariesFromSRGB, linear);
        return transfer(thisSpace);
    }

    @Override
    public float[] toCIEXYZ(float[] colorvalue) {
        float[] linear = linearize(colorvalue);
        float[] linearXYZ = MathHelper.matrixMutliply(this.primariesToXYZD50, linear);
        return linearXYZ;
    }

    @Override
    public float[] fromCIEXYZ(float[] colorvalue) {
        float[] thisSpace = MathHelper.matrixMutliply(this.primariesFromXYZD50, colorvalue);
        return transfer(thisSpace);
    }
}
