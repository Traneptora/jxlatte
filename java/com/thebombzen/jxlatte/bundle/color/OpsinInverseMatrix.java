package com.thebombzen.jxlatte.bundle.color;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.MathHelper;
import com.thebombzen.jxlatte.util.TaskList;

public class OpsinInverseMatrix {

    private static final double[][] DEFAULT_MATRIX = {
        {11.031566901960783D, -9.866943921568629D, -0.16462299647058826D},
        {-3.254147380392157D, 4.418770392156863D, -0.16462299647058826D},
        {-3.6588512862745097D, 2.7129230470588235D, 1.9459282392156863D}
    };

    private static final double[] DEFAULT_OPSIN_BIAS = {
        -0.0037930732552754493D, -0.0037930732552754493D, -0.0037930732552754493D
    };

    private static final double[] DEFAULT_QUANT_BIAS = {
        1D - 0.05465007330715401D, 1D - 0.07005449891748593D, 1D - 0.049935103337343655D
    };

    private static final double DEFAULT_QBIAS_NUMERATOR = 0.145D;

    private double[][] matrix;
    private double[] opsinBias;
    private double[] cbrtOpsinBias;
    private double[] quantBias;
    public final double quantBiasNumerator;

    public OpsinInverseMatrix() {
        matrix = DEFAULT_MATRIX;
        opsinBias = DEFAULT_OPSIN_BIAS;
        quantBias = DEFAULT_QUANT_BIAS;
        quantBiasNumerator = DEFAULT_QBIAS_NUMERATOR;
        bakeCbrtBias();
    }

    public OpsinInverseMatrix(Bitreader reader) throws IOException {
        if (reader.readBool()) {
            matrix = DEFAULT_MATRIX;
            opsinBias = DEFAULT_OPSIN_BIAS;
            quantBias = DEFAULT_QUANT_BIAS;
            quantBiasNumerator = DEFAULT_QBIAS_NUMERATOR;
        } else {
            matrix = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    matrix[i][j] = reader.readF16();
                }
            }
            opsinBias = new double[3];
            for (int i = 0; i < 3; i++)
                opsinBias[i] = reader.readF16();
            quantBias = new double[3];
            for (int i = 0; i < 3; i++)
                quantBias[i] = reader.readF16();
            quantBiasNumerator = reader.readF16();
        }
        bakeCbrtBias();
    }

    private void bakeCbrtBias() {
        cbrtOpsinBias = new double[3];
        for (int c = 0; c < 3; c++)
            cbrtOpsinBias[c] = MathHelper.signedPow(opsinBias[c], 1D/3D);
    }

    /** 
     * @return linear sRGB
     */
    public void invertXYB(double[][][] buffer, double intensityTarget) {
        if (buffer.length < 3)
            throw new IllegalArgumentException("Can only XYB on 3 channels");
        TaskList<Void> tasks = new TaskList<>();
        for (int y_ = 0; y_ < buffer[0].length; y_++) {
            final int y = y_;
            tasks.submit(() -> {
                for (int x = 0; x < buffer[0][y].length; x++) {
                    double gammaL = buffer[1][y][x] + buffer[0][y][x] - cbrtOpsinBias[0];
                    double gammaM = buffer[1][y][x] - buffer[0][y][x] - cbrtOpsinBias[1];
                    double gammaS = buffer[2][y][x] - cbrtOpsinBias[2];
                    double itScale = 255D / intensityTarget;
                    double mixL = (gammaL * gammaL * gammaL + opsinBias[0]) * itScale;
                    double mixM = (gammaM * gammaM * gammaM + opsinBias[1]) * itScale;
                    double mixS = (gammaS * gammaS * gammaS + opsinBias[2]) * itScale;
                    for (int c = 0; c < 3; c++)
                        buffer[c][y][x] = matrix[c][0] * mixL + matrix[c][1] * mixM + matrix[c][2] * mixS;
                }
            });
        }
        tasks.collect();
    }
}
