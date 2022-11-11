package com.thebombzen.jxlatte.frame.vardct;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.modular.ModularChannel;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.MathHelper;

public class HFGlobal {

    private static final double[] afvFreqs = {0, 0, 0.8517778890324296, 5.37778436506804,
        0, 0, 4.734747904497923, 5.449245381693219, 1.6598270267479331, 4, 7.275749096817861,
        10.423227632456525, 2.662932286148962,  7.630657783650829, 8.962388608184032, 12.97166202570235};

    private static double[] prepend(double a, double... values) {
        return DoubleStream.concat(DoubleStream.of(a), DoubleStream.of(values)).toArray();
    }

    private static double[][] readDCTParams(Bitreader reader) throws IOException {
        int numParams = 1 + reader.readBits(4);
        double[][] vals = new double[3][numParams];
        for (int c = 0; c < 3; c++) {
            for (int i = 0; i < numParams; i++) {
                vals[c][i] = reader.readF16();
            }
            vals[c][0] *= 64D;
        }
        return vals;
    }

    private static double interpolate(double pos, double max, double[] bands) {
        if (bands.length == 1)
            return bands[0];
        double scaledPos = pos * (bands.length - 1) / max;
        int scaledIndex = (int)scaledPos;
        double fracIndex = scaledPos - scaledIndex;
        double a = bands[scaledIndex];
        double b = bands[scaledIndex + 1];
        return a * Math.pow(b / a, fracIndex);
    }

    private static double quantMult(double v) {
        return v >= 0 ? 1 + v : 1 / (1 - v);
    }

    private static double[][] getDCTQuantWeights(int width, int height, double[] params) {
        double[] bands = new double[params.length];
        bands[0] = params[0];
        for (int i = 1; i < params.length; i++) {
            bands[i] = bands[i - 1] * quantMult(params[i]);
        }
        double[][] weights = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double dx = (double)x / (width - 1);
                double dy = (double)y / (height - 1);
                double dist = Math.sqrt(dx * dx + dy * dy);
                weights[y][x] = interpolate(dist, MathHelper.SQRT_2 + 1e-6D, bands);
            }
        }
        return weights;
    }

    public final DCTParams[] defaultParams = new DCTParams[17];
    public final DCTParams[] params;
    public final double[][][][] weights;
    public final int numHfPresets;

    private void setupDefaultParams() {
        defaultParams[0] = new DCTParams(new double[][]{
            {3150.0, 0.0, -0.4, -0.4, -0.4, -2.0},
             {560.0, 0.0, -0.3, -0.3, -0.3, -0.3},
             {512.0, -2.0, -1.0, 0.0, -1.0, -2.0},
        }, new double[][]{}, TransformType.MODE_DCT);
        defaultParams[1] = new DCTParams(new double[][]{}, new double[][]{
                {280.0, 3160.0, 3160.0},
                {60.0, 864.0, 864.0},
                {18.0, 200.0, 200.0},
        }, TransformType.MODE_HORNUSS);
        defaultParams[2] = new DCTParams(new double[][]{}, new double[][]{
            {3840.0, 2560.0, 1280.0, 640.0, 480.0, 300.0},
            {960.0, 640.0, 320.0, 180.0, 140.0, 120.0},
            {640.0, 320.0, 128.0, 64.0, 32.0, 16.0},
        }, TransformType.MODE_DCT2);
        double[][] dct4x4params = {
            {843.649426659137152, 0.0, 0.0, 0.0},
            {289.6948005482115584, 0.0, 0.0, 0.0},
            {137.04727932185712576, -0.25, -0.25, -0.5},
        };
        defaultParams[3] = new DCTParams(dct4x4params, new double[][]{
            {1.0, 1.0},
            {1.0, 1.0},
            {1.0, 1.0},
        }, dct4x4params, TransformType.MODE_DCT4);
        defaultParams[4] = new DCTParams(new double[][]{
            {8996.8725711814115328, -1.3000777393353804, -0.49424529824571225, -0.439093774457103443,
                -0.6350101832695744, -0.90177264050827612, -1.6162099239887414},
            {3191.48366296844234752, -0.67424582104194355, -0.80745813428471001, -0.44925837484843441,
                -0.35865440981033403, -0.31322389111877305, -0.37615025315725483},
            {1157.50408145487200256, -2.0531423165804414, -1.4, -0.50687130033378396,
                -0.42708730624733904, -1.4856834539296244, -4.9209142884401604},
        }, new double[][]{}, TransformType.MODE_DCT);
        defaultParams[5] = new DCTParams(new double[][]{
            {15718.40830982518931456, -1.025, -0.98, -0.9012, -0.4, -0.48819395464, -0.421064, -0.27},
            {7305.7636810695983104, -0.8041958212306401, -0.7633036457487539, -0.55660379990111464,
                -0.49785304658857626, -0.43699592683512467, -0.40180866526242109, -0.27321683125358037},
            {3803.53173721215041536, -3.060733579805728, -2.0413270132490346, -2.0235650159727417,
                -0.5495389509954993, -0.4, -0.4, -0.3},
        }, new double[][]{}, TransformType.MODE_DCT);
        defaultParams[6] = new DCTParams(new double[][]{
            {7240.7734393502, -0.7, -0.7, -0.2, -0.2, -0.2, -0.5},
            {1448.15468787004, -0.5, -0.5, -0.5, -0.2, -0.2, -0.2},
            {506.854140754517, -1.4, -0.2, -0.5, -0.5, -1.5, -3.6},
        }, new double[][]{}, TransformType.MODE_DCT);
        defaultParams[7] = new DCTParams(new double[][]{
            {16283.2494710648897, -1.7812845336559429, -1.6309059012653515,
                -1.0382179034313539, -0.85, -0.7, -0.9, -1.2360638576849587},
            {5089.15750884921511936, -0.320049391452786891, -0.35362849922161446,
                -0.30340000000000003, -0.61, -0.5, -0.5, -0.6},
            {3397.77603275308720128, -0.321327362693153371, -0.34507619223117997,
                -0.70340000000000003, -0.9, -1.0, -1.0, -1.1754605576265209},
        }, new double[][]{}, TransformType.MODE_DCT);
        defaultParams[8] = new DCTParams(new double[][]{
            {13844.97076442300573, -0.97113799999999995, -0.658, -0.42026, -0.22712, -0.2206, -0.226, -0.6},
            {4798.964084220744293, -0.61125308982767057, -0.83770786552491361, -0.79014862079498627,
                -0.2692727459704829, -0.38272769465388551, -0.22924222653091453, -0.20719098826199578},
            {1807.236946760964614, -1.2, -1.2, -0.7, -0.7, -0.7, -0.4, -0.5},
        }, new double[][]{}, TransformType.MODE_DCT);
        double[][] dct4x8Params = {
            {2198.050556016380522, -0.96269623020744692, -0.76194253026666783, -0.6551140670773547},
            {764.3655248643528689, -0.92630200888366945, -0.9675229603596517, -0.27845290869168118},
            {527.107573587542228, -1.4594385811273854, -1.450082094097871593, -1.5843722511996204},
        };
        defaultParams[9] = new DCTParams(dct4x8Params, new double[][]{{1.0}, {1.0}, {1.0}}, TransformType.MODE_DCT4_8);
        defaultParams[10] = new DCTParams(dct4x8Params, new double[][]{
            {3072, 3072, 256, 256, 256, 414, 0.0, 0.0, 0.0},
            {1024, 1024, 50.0, 50.0, 50.0, 58, 0.0, 0.0, 0.0},
            {384, 384, 12.0, 12.0, 12.0, 22, -0.25, -0.25, -0.25},
        }, dct4x4params, TransformType.MODE_AFV);
        double[] seqA = {-1.025, -0.78, -0.65012, -0.19041574084286472,
            -0.20819395464, -0.421064, -0.32733845535848671};
        double[] seqB = {-0.3041958212306401, 0.3633036457487539, -0.35660379990111464, -0.3443074455424403,
            -0.33699592683512467, -0.30180866526242109, -0.27321683125358037};
        double[] seqC = {-1.2, -1.2, -0.8, -0.7, -0.7, -0.4, -0.5};
        defaultParams[11] = new DCTParams(new double[][]{
            prepend(23966.1665298448605, seqA),
            prepend(8380.19148390090414, seqB),
            prepend(4493.02378009847706, seqC),
        }, new double[][]{}, TransformType.MODE_DCT);
        defaultParams[12] = new DCTParams(new double[][]{
            prepend(15358.89804933239925, seqA),
            prepend(5597.360516150652990, seqB),
            prepend(2919.961618960011210, seqC),
        }, new double[][]{}, TransformType.MODE_DCT);
        defaultParams[13] = new DCTParams(new double[][]{
            prepend(47932.3330596897210, seqA),
            prepend(16760.38296780180828, seqB),
            prepend(8986.04756019695412, seqC),
        }, new double[][]{}, TransformType.MODE_DCT);
        defaultParams[14] = new DCTParams(new double[][]{
            prepend(30717.796098664792, seqA),
            prepend(11194.72103230130598, seqB),
            prepend(5839.92323792002242, seqC),
        }, new double[][]{}, TransformType.MODE_DCT);
        defaultParams[15] = new DCTParams(new double[][]{
            prepend(95864.6661193794420, seqA),
            prepend(33520.76593560361656, seqB),
            prepend(17972.09512039390824, seqC),
        }, new double[][]{}, TransformType.MODE_DCT);
        defaultParams[16] = new DCTParams(new double[][]{
            prepend(61435.5921973295970, seqA),
            prepend(24209.44206460261196, seqB),
            prepend(12979.84647584004484, seqC),
        }, new double[][]{}, TransformType.MODE_DCT);
    }

    private void setupDCTParam(Bitreader reader, Frame frame, int index) throws IOException {
        int encodingMode = reader.readBits(3);
        TransformType.validateIndex(index, encodingMode);
        double[][] m;
        switch (encodingMode) {
            case TransformType.MODE_LIBRARY:
                params[index] = defaultParams[index];
                break;
            case TransformType.MODE_HORNUSS:
                m = new double[3][3];
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 3; x++) {
                        m[y][x] = 64D * reader.readF16();
                    }
                }
                params[index] = new DCTParams(new double[][]{}, m, encodingMode);
                break;
            case TransformType.MODE_DCT2:
                m = new double[3][6];
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 6; x++) {
                        m[y][x] = 64D * reader.readF16();
                    }
                }
                params[index] = new DCTParams(new double[][]{}, m, encodingMode);
                break;
            case TransformType.MODE_DCT4:
                m = new double[3][2];
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 2; x++) {
                        m[y][x] = 64D * reader.readF16();
                    }
                }
                params[index] = new DCTParams(readDCTParams(reader), m, encodingMode);
                break;
            case TransformType.MODE_DCT:
                params[index] = new DCTParams(readDCTParams(reader), new double[][]{}, encodingMode);
                break;
            case TransformType.MODE_RAW:
                double den = reader.readF16();
                reader.zeroPadToByte();
                TransformType tt = Stream.of(TransformType.values())
                    .filter(t -> t.parameterIndex == index).findFirst().get();
                ModularChannelInfo[] info = new ModularChannelInfo[3];
                info[0] = new ModularChannel(tt.matrixWidth, tt.matrixHeight, 0, 0);
                info[1] = new ModularChannel(tt.matrixWidth, tt.matrixHeight, 0, 0);
                info[2] = new ModularChannel(tt.matrixWidth, tt.matrixHeight, 0, 0);
                ModularStream stream = new ModularStream(reader, frame.getLFGlobal().gModular.globalTree,
                    frame, 1 + 3 * frame.getNumLFGroups() + index, info);
                stream.decodeChannels(reader, false);
                m = new double[3][tt.matrixWidth * tt.matrixHeight];
                int[][][] b = stream.getDecodedBuffer();
                for (int c = 0; c < 3; c++) {
                    for (int y = 0; y < tt.matrixHeight; y++) {
                        for (int x = 0; x < tt.matrixWidth; x++)
                            m[c][y * tt.matrixWidth + x] = b[c][y][x];
                    }
                }
                params[index] = new DCTParams(new double[][]{}, m, encodingMode, den);
                break;
            case TransformType.MODE_AFV:
                m = new double[3][9];
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 9; x++) {
                        m[y][x] = reader.readF16();
                        if (x < 6)
                            m[y][x] *= 64D;
                    }
                }
                double[][] d = readDCTParams(reader);
                double[][] f = readDCTParams(reader);
                params[index] = new DCTParams(d, m, f, encodingMode);
                break;
            default:
                throw new IllegalStateException("Challenge complete how did we get here");
        }
    }

    private double[][] getAFVTransformWeights(int index, int c) throws InvalidBitstreamException {
        double[][] weights4x8 = getDCTQuantWeights(8, 4, params[index].dctParam[c]);
        double[][] weights4x4 = getDCTQuantWeights(4, 4, params[index].params4x4[c]);
        double low = 0.8517778890324296;
        double high = 12.97166202570235;
        double[] bands = new double[4];
        bands[0] = params[index].param[c][5];
        if (bands[0] < 0)
            throw new InvalidBitstreamException("Illegal negative band value");
        for (int i = 1; i < 4; i++) {
            bands[i] = bands[i - 1] * quantMult(params[index].param[c][i + 5]);
            if (bands[i] < 0)
                throw new InvalidBitstreamException("Illegal negative band value");
        }
        double[][] weight = new double[8][8];
        weight[0][0] = 1D;
        weight[1][0] = params[index].param[c][0];
        weight[0][1] = params[index].param[c][1];
        weight[2][0] = params[index].param[c][2];
        weight[0][2] = params[index].param[c][3];
        weight[2][2] = params[index].param[c][4];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (x < 2 && y < 2)
                    continue;
                weight[2 * y][2 * x] = interpolate(afvFreqs[y * 4 + x] - low, high - low + 1e-6D, bands);
            }
        }
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 8; x++) {
                if (x == 0 && y == 0)
                    continue;
                weight[2 * y + 1][x] = weights4x8[y][x];
            }
        }
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (x == 0 && y == 0)
                    continue;
                weight[2 * y][2 * x + 1] = weights4x4[y][x];
            }
        }
        return weight;
    }

    private void generateWeights(int index) throws InvalidBitstreamException {
        TransformType tt = Stream.of(TransformType.values())
                    .filter(t -> t.parameterIndex == index).findFirst().get();
        for (int c = 0; c < 3; c++) {
            double[][] w;
            switch (params[index].mode) {
                case TransformType.MODE_DCT:
                    weights[index][c] = getDCTQuantWeights(tt.matrixWidth, tt.matrixHeight, params[index].dctParam[c]);
                    break;
                case TransformType.MODE_DCT4:
                    weights[index][c] = new double[8][8];
                    w = getDCTQuantWeights(4, 4, params[index].dctParam[c]);
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            weights[index][c][y][x] = w[y/2][x/2];
                        }
                    }
                    weights[index][c][1][0] /= params[index].param[c][0];
                    weights[index][c][0][1] /= params[index].param[c][0];
                    weights[index][c][1][1] /= params[index].param[c][1];
                    break;
                case TransformType.MODE_DCT2:
                    w = new double[8][8];
                    w[0][0] = 1D;
                    w[0][1] = w[1][0] = params[index].param[c][0];
                    w[1][1] = params[index].param[c][1];
                    for (int y = 0; y < 2; y++) {
                        for (int x = 2; x < 4; x++) {
                            w[y][x] = w[x][y] = params[index].param[c][2];
                        }
                    }
                    for (int y = 2; y < 4; y++) {
                        for (int x = 2; x < 4; x++) {
                            w[y][x] = params[index].param[c][3];
                        }
                    }
                    for (int y = 0; y < 4; y++) {
                        for (int x = 4; x < 8; x++) {
                            w[y][x] = w[x][y] = params[index].param[c][4];
                        }
                    }
                    for (int y = 4; y < 8; y++) {
                        for (int x = 4; x < 8; x++) {
                            w[y][x] = params[index].param[c][5];
                        }
                    }
                    weights[index][c] = w;
                    break;
                case TransformType.MODE_HORNUSS:
                    w = new double[8][8];
                    for (int y = 0; y < 8; y++)
                        Arrays.fill(w[y], params[index].param[c][0]);
                    w[1][1] = params[index].param[c][2];
                    w[0][1] = w[1][0] = params[index].param[c][1];
                    w[0][0] = 1D;
                    weights[index][c] = w;
                    break;
                case TransformType.MODE_DCT4_8:
                    weights[index][c] = new double[8][8];
                    w = getDCTQuantWeights(8, 4, params[index].dctParam[c]);
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            weights[index][c][y][x] = w[y/2][x];
                        }
                    }
                    weights[index][c][1][0] /= params[index].param[c][0];
                    break;
                case TransformType.MODE_AFV:
                    weights[index][c] = getAFVTransformWeights(index, c);
                    break;
                case TransformType.MODE_RAW:
                    weights[index][c] = new double[tt.matrixHeight][tt.matrixWidth];
                    for (int y = 0; y < tt.matrixHeight; y++) {
                        for (int x = 0; x < tt.matrixWidth; x++) {
                            weights[index][c][y][x] = params[index].param[c][y * tt.matrixWidth + x]
                                / params[index].denominator;
                        }
                    }
                default:
                    throw new UnsupportedOperationException("Unsupported DCT Type at this time");
            }
            for (int y = 0; y < tt.matrixHeight; y++) {
                for (int x = 0; x < tt.matrixWidth; x++) {
                    if (weights[index][c][y][x] <= 0D || !Double.isFinite(weights[index][c][y][x]))
                        throw new InvalidBitstreamException("Negative or infinite weight: " + index + ", " + c);
                    weights[index][c][y][x] = 1D / weights[index][c][y][x];
                }
            }
        }
    }

    public HFGlobal(Bitreader reader, Frame frame) throws IOException {
        boolean quantAllDefault = reader.readBool();
        setupDefaultParams();
        if (quantAllDefault) {
            params = defaultParams;
        } else {
            params = new DCTParams[17];
            for (int i = 0; i < 17; i++) {
                setupDCTParam(reader, frame, i);
            }
        }
        weights = new double[17][3][][];
        for (int i = 0; i < 17; i++) {
            generateWeights(i);
        }
        numHfPresets = 1 + reader.readBits(MathHelper.ceilLog1p(frame.getNumGroups() - 1));
    }
}
