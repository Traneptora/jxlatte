package com.thebombzen.jxlatte.bundle.color;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import com.thebombzen.jxlatte.io.Bitreader;

public class OpsinInverseMatrix {

    public static final float[][] DEFAULT_MATRIX = {
        {11.031566901960783f, -9.866943921568629f, -0.16462299647058826f},
        {-3.254147380392157f, 4.418770392156863f, -0.16462299647058826f},
        {-3.6588512862745097f, 2.7129230470588235f, 1.9459282392156863f}
    };

    public static final float[] DEFAULT_OPSIN_BIAS = {
        -0.0037930732552754493f, -0.0037930732552754493f, -0.0037930732552754493f
    };

    public static final float[] DEFAULT_QUANT_BIAS = {
        1f-0.05465007330715401f, 1f-0.07005449891748593f, 1f-0.049935103337343655f
    };

    public static final float DEFAULT_QBIAS_NUMERATOR = 0.145f;

    public final float[][] matrix;
    public final float[] opsinBias;
    public final float[] quantBias;
    public final float quantBiasNumerator;

    public OpsinInverseMatrix() {
        matrix = DEFAULT_MATRIX;
        opsinBias = DEFAULT_OPSIN_BIAS;
        quantBias = DEFAULT_QUANT_BIAS;
        quantBiasNumerator = DEFAULT_QBIAS_NUMERATOR;
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
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.deepHashCode(matrix);
        result = prime * result + Arrays.hashCode(opsinBias);
        result = prime * result + Arrays.hashCode(quantBias);
        result = prime * result + Objects.hash(quantBiasNumerator);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        OpsinInverseMatrix other = (OpsinInverseMatrix) obj;
        return Arrays.deepEquals(matrix, other.matrix) && Arrays.equals(opsinBias, other.opsinBias)
                && Arrays.equals(quantBias, other.quantBias)
                && Float.floatToIntBits(quantBiasNumerator) == Float.floatToIntBits(other.quantBiasNumerator);
    }

    @Override
    public String toString() {
        return "OpsinInverseMatrix [matrix=" + Arrays.toString(matrix) + ", opsinBias=" + Arrays.toString(opsinBias)
                + ", quantBias=" + Arrays.toString(quantBias) + ", quantBiasNumerator=" + quantBiasNumerator + "]";
    }
    
}
