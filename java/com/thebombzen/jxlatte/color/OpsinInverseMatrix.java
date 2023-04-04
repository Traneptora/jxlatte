package com.thebombzen.jxlatte.color;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.FlowHelper;
import com.thebombzen.jxlatte.util.IntPoint;
import com.thebombzen.jxlatte.util.MathHelper;

public class OpsinInverseMatrix {

    private static final float[][] DEFAULT_MATRIX = {
        {11.031566901960783f, -9.866943921568629f, -0.16462299647058826f},
        {-3.254147380392157f, 4.418770392156863f, -0.16462299647058826f},
        {-3.6588512862745097f, 2.7129230470588235f, 1.9459282392156863f}
    };

    private static final float[] DEFAULT_OPSIN_BIAS = {
        -0.0037930732552754493f, -0.0037930732552754493f, -0.0037930732552754493f
    };

    private static final float[] DEFAULT_QUANT_BIAS = {
        1f - 0.05465007330715401f, 1f - 0.07005449891748593f, 1f - 0.049935103337343655f
    };

    private static final float DEFAULT_QBIAS_NUMERATOR = 0.145f;

    private float[][] matrix;
    private float[] opsinBias;
    private float[] cbrtOpsinBias;
    public final float[] quantBias;
    public final float quantBiasNumerator;
    public final CIEPrimaries primaries;
    public final CIEXY whitePoint;

    public OpsinInverseMatrix() {
        this(ColorManagement.PRI_SRGB, ColorManagement.WP_D65);
    }

    private OpsinInverseMatrix(CIEPrimaries primaries, CIEXY whitePoint) {
        matrix = DEFAULT_MATRIX;
        opsinBias = DEFAULT_OPSIN_BIAS;
        quantBias = DEFAULT_QUANT_BIAS;
        quantBiasNumerator = DEFAULT_QBIAS_NUMERATOR;
        this.primaries = primaries;
        this.whitePoint = whitePoint;
        bakeCbrtBias();
    }

    public OpsinInverseMatrix(Bitreader reader) throws IOException {
        if (reader.readBool()) {
            matrix = DEFAULT_MATRIX;
            opsinBias = DEFAULT_OPSIN_BIAS;
            quantBias = DEFAULT_QUANT_BIAS;
            quantBiasNumerator = DEFAULT_QBIAS_NUMERATOR;
        } else {
            matrix = new float[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    matrix[i][j] = reader.readF16();
                }
            }
            opsinBias = new float[3];
            for (int i = 0; i < 3; i++)
                opsinBias[i] = reader.readF16();
            quantBias = new float[3];
            for (int i = 0; i < 3; i++)
                quantBias[i] = reader.readF16();
            quantBiasNumerator = reader.readF16();
        }
        this.primaries = ColorManagement.PRI_SRGB;
        this.whitePoint = ColorManagement.WP_D65;
        bakeCbrtBias();
    }

    private void bakeCbrtBias() {
        cbrtOpsinBias = new float[3];
        for (int c = 0; c < 3; c++)
            cbrtOpsinBias[c] = MathHelper.signedPow(opsinBias[c], 1f/3f);
    }

    public OpsinInverseMatrix getMatrix(CIEPrimaries primaries, CIEXY whitePoint) {
        OpsinInverseMatrix opsin = new OpsinInverseMatrix(primaries, whitePoint);
        opsin.matrix = MathHelper.matrixMutliply(
            ColorManagement.getConversionMatrix(primaries, whitePoint, this.primaries, this.whitePoint),
            this.matrix);
        return opsin;
    }

    public void invertXYB(float[][][] buffer, float intensityTarget, FlowHelper flowHelper) {
        if (buffer.length < 3)
            throw new IllegalArgumentException("Can only XYB on 3 channels");
        final float itScale = 255f / intensityTarget;
        flowHelper.parallelIterate(IntPoint.sizeOf(buffer[0]), (x, y) -> {
            float gammaL = buffer[1][y][x] + buffer[0][y][x] - cbrtOpsinBias[0];
            float gammaM = buffer[1][y][x] - buffer[0][y][x] - cbrtOpsinBias[1];
            float gammaS = buffer[2][y][x] - cbrtOpsinBias[2];
            float mixL = gammaL * gammaL * gammaL + opsinBias[0];
            float mixM = gammaM * gammaM * gammaM + opsinBias[1];
            float mixS = gammaS * gammaS * gammaS + opsinBias[2];
            for (int c = 0; c < 3; c++)
                buffer[c][y][x] = (matrix[c][0] * mixL + matrix[c][1] * mixM +  matrix[c][2] * mixS) * itScale;
        });
    }
}
