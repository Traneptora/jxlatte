package com.traneptora.jxlatte.color;

import java.io.IOException;
import java.util.Arrays;

import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.FlowHelper;
import com.traneptora.jxlatte.util.MathHelper;

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

    private final float[][] matrix;
    private final float[] opsinBias;
    private float[] cbrtOpsinBias;
    public final float[] quantBias;
    public final float quantBiasNumerator;
    public final CIEPrimaries primaries;
    public final CIEXY whitePoint;

    public OpsinInverseMatrix() {
        this(ColorManagement.PRI_SRGB, ColorManagement.WP_D65, DEFAULT_MATRIX,
            DEFAULT_OPSIN_BIAS, DEFAULT_QUANT_BIAS, DEFAULT_QBIAS_NUMERATOR);
    }

    private OpsinInverseMatrix(CIEPrimaries primaries, CIEXY whitePoint, float[][] matrix,
            float[] opsinBias, float[] quantBias, float quantBiasNumerator) {
        this.matrix = matrix;
        this.opsinBias = opsinBias;
        this.quantBias = quantBias;
        this.quantBiasNumerator = quantBiasNumerator;
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

    @Override
    public String toString() {
        return String.format(
                "OpsinInverseMatrix [matrix=%s, opsinBias=%s, cbrtOpsinBias=%s, quantBias=%s, quantBiasNumerator=%s, primaries=%s, whitePoint=%s]",
                Arrays.deepToString(matrix), Arrays.toString(opsinBias), Arrays.toString(cbrtOpsinBias),
                Arrays.toString(quantBias), quantBiasNumerator, primaries, whitePoint);
    }

    public OpsinInverseMatrix getMatrix(CIEPrimaries primaries, CIEXY whitePoint) {
        float[][] conversion = ColorManagement.getConversionMatrix(primaries, whitePoint,
            this.primaries, this.whitePoint);
        float[][] matrix = MathHelper.matrixMutliply(conversion, this.matrix);
        return new OpsinInverseMatrix(primaries, whitePoint, matrix,
            this.opsinBias, this.quantBias, this.quantBiasNumerator);
    }

    public void invertXYB(float[][][] buffer, float intensityTarget, FlowHelper flowHelper) {
        if (buffer.length < 3)
            throw new IllegalArgumentException("Can only XYB on 3 channels");
        final float itScale = 255f / intensityTarget;
        for (int y = 0; y < buffer[0].length; y++) {
            for (int x = 0; x < buffer[0][y].length; x++) {
                float gammaL = buffer[1][y][x] + buffer[0][y][x] - cbrtOpsinBias[0];
                float gammaM = buffer[1][y][x] - buffer[0][y][x] - cbrtOpsinBias[1];
                float gammaS = buffer[2][y][x] - cbrtOpsinBias[2];
                float mixL = gammaL * gammaL * gammaL + opsinBias[0];
                float mixM = gammaM * gammaM * gammaM + opsinBias[1];
                float mixS = gammaS * gammaS * gammaS + opsinBias[2];
                for (int c = 0; c < 3; c++)
                    buffer[c][y][x] = (matrix[c][0] * mixL + matrix[c][1] * mixM + matrix[c][2] * mixS) * itScale;
            }
        }
    }
}
