package com.traneptora.jxlatte.color;

import java.io.IOException;
import java.util.Arrays;

import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.MathHelper;

public class OpsinInverseMatrix {

    private static final float[][] DEFAULT_MATRIX = {
        {11.031566901960783f, -9.866943921568629f, -0.16462299647058826f},
        {-3.254147380392157f, 4.418770392156863f, -0.16462299647058826f},
        {-3.6588512862745097f, 2.7129230470588235f, 1.9459282392156863f},
    };

    private static final float[] DEFAULT_OPSIN_BIAS = {
        -0.0037930732552754493f,
        -0.0037930732552754493f,
        -0.0037930732552754493f,
    };

    private static final float[] DEFAULT_QUANT_BIAS = {
        0.945349926692846f, 0.9299455010825141f, 0.9500648966626564f,
    };

    private static final float DEFAULT_QBIAS_NUMERATOR = 0.145f;

    private final float[][] matrix;
    private final float[] opsinBias;
    private final float[] cbrtOpsinBias;
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
        this.cbrtOpsinBias = new float[3];
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
        this.cbrtOpsinBias = new float[3];
        bakeCbrtBias();
    }

    private void bakeCbrtBias() {
        for (int c = 0; c < 3; c++)
            cbrtOpsinBias[c] = (float)Math.cbrt(opsinBias[c]);
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

    /**
     * Inverts in place
     */
    public void invertXYB(float[][][] buffer, float intensityTarget) {
        if (buffer.length < 3)
            throw new IllegalArgumentException("Can only XYB on 3 channels");
        final float itScale = 255f / intensityTarget;
        final float[][] scaledMatrix = new float[3][3];
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++)
                scaledMatrix[y][x] = matrix[y][x] * itScale;
        }
        final float[][] xybXRBuffer = buffer[0];
        final float[][] xybYGBuffer = buffer[1];
        final float[][] xybBBBuffer = buffer[2];
        for (int y = 0; y < buffer[0].length; y++) {
            final float[] xybXRBufferRow = xybXRBuffer[y];
            final float[] xybYGBufferRow = xybYGBuffer[y];
            final float[] xybBBBufferRow = xybBBBuffer[y];
            for (int x = 0; x < buffer[0][y].length; x++) {
                final float xybX = xybXRBufferRow[x];
                final float xybY = xybYGBufferRow[x];
                final float xybB = xybBBBufferRow[x];
                final float gammaL = xybY + xybX - cbrtOpsinBias[0];
                final float gammaM = xybY - xybX - cbrtOpsinBias[1];
                final float gammaS = xybB - cbrtOpsinBias[2];
                final float mixL = (gammaL * gammaL) * gammaL + opsinBias[0];
                final float mixM = (gammaM * gammaM) * gammaM + opsinBias[1];
                final float mixS = (gammaS * gammaS) * gammaS + opsinBias[2];
                xybXRBufferRow[x] = scaledMatrix[0][0] * mixL + scaledMatrix[0][1] * mixM + scaledMatrix[0][2] * mixS;
                xybYGBufferRow[x] = scaledMatrix[1][0] * mixL + scaledMatrix[1][1] * mixM + scaledMatrix[1][2] * mixS;
                xybBBBufferRow[x] = scaledMatrix[2][0] * mixL + scaledMatrix[2][1] * mixM + scaledMatrix[2][2] * mixS;
            }
        }
    }
}
