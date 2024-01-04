package com.traneptora.jxlatte.frame.vardct;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.modular.ModularChannel;
import com.traneptora.jxlatte.frame.modular.ModularChannelInfo;
import com.traneptora.jxlatte.frame.modular.ModularStream;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.Loggers;
import com.traneptora.jxlatte.util.FlowHelper;
import com.traneptora.jxlatte.util.IntPoint;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.functional.FunctionalHelper;

public class HFGlobal {

    private static final DCTParams[] defaultParams = new DCTParams[17];
    private static final float[][][][] defaultWeights = new float[17][3][][];

    private static final float[] afvFreqs = {0, 0, 0.8517778890324296f, 5.37778436506804f,
        0, 0, 4.734747904497923f, 5.449245381693219f, 1.6598270267479331f, 4, 7.275749096817861f,
        10.423227632456525f, 2.662932286148962f, 7.630657783650829f, 8.962388608184032f, 12.97166202570235f};

    private static float[] prepend(float a, float... values) {
        float[] arr = new float[values.length + 1];
        arr[0] = a;
        System.arraycopy(values, 0, arr, 1, values.length);
        return arr;
    }

    private static float[][] readDCTParams(Bitreader reader) throws IOException {
        int numParams = 1 + reader.readBits(4);
        float[][] vals = new float[3][numParams];
        for (int c = 0; c < 3; c++) {
            for (int i = 0; i < numParams; i++) {
                vals[c][i] = reader.readF16();
            }
            vals[c][0] *= 64D;
        }
        return vals;
    }

    private static float interpolate(float pos, float max, float[] bands) {
        if (bands.length == 1)
            return bands[0];
        float scaledPos = pos * (bands.length - 1) / max;
        int scaledIndex = (int)scaledPos;
        float fracIndex = scaledPos - scaledIndex;
        float a = bands[scaledIndex];
        float b = bands[scaledIndex + 1];
        return a * (float)Math.pow(b / a, fracIndex);
    }

    private static float quantMult(float v) {
        return v >= 0 ? 1 + v : 1 / (1 - v);
    }

    private static float[][] getDCTQuantWeights(int width, int height, float[] params) {
        float[] bands = new float[params.length];
        bands[0] = params[0];
        for (int i = 1; i < bands.length; i++) {
            bands[i] = bands[i - 1] * quantMult(params[i]);
        }
        float[][] weights = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float dx = (float)x / (width - 1);
                float dy = (float)y / (height - 1);
                float dist = (float)Math.sqrt(dx * dx + dy * dy);
                weights[y][x] = interpolate(dist, (float)MathHelper.SQRT_2 + 1e-6f, bands);
            }
        }
        return weights;
    }

    private static void setupDefaultParams() {
        defaultParams[0] = new DCTParams(new float[][]{
            {3150.0f, 0.0f, -0.4f, -0.4f, -0.4f, -2.0f},
             {560.0f, 0.0f, -0.3f, -0.3f, -0.3f, -0.3f},
             {512.0f, -2.0f, -1.0f, 0.0f, -1.0f, -2.0f},
        }, null, TransformType.MODE_DCT);
        defaultParams[1] = new DCTParams(null, new float[][]{
                {280.0f, 3160.0f, 3160.0f},
                {60.0f, 864.0f, 864.0f},
                {18.0f, 200.0f, 200.0f},
        }, TransformType.MODE_HORNUSS);
        defaultParams[2] = new DCTParams(null, new float[][]{
            {3840.0f, 2560.0f, 1280.0f, 640.0f, 480.0f, 300.0f},
            {960.0f, 640.0f, 320.0f, 180.0f, 140.0f, 120.0f},
            {640.0f, 320.0f, 128.0f, 64.0f, 32.0f, 16.0f},
        }, TransformType.MODE_DCT2);
        float[][] dct4x4params = {
            {2200f, 0.0f, 0.0f, 0.0f},
            {392f, 0.0f, 0.0f, 0.0f},
            {112f, -0.25f, -0.25f, -0.5f},
        };
        defaultParams[3] = new DCTParams(dct4x4params, new float[][]{
            {1.0f, 1.0f},
            {1.0f, 1.0f},
            {1.0f, 1.0f},
        }, dct4x4params, TransformType.MODE_DCT4);
        defaultParams[4] = new DCTParams(new float[][]{
            {8996.8725711814115328f, -1.3000777393353804f, -0.49424529824571225f, -0.439093774457103443f,
                -0.6350101832695744f, -0.90177264050827612f, -1.6162099239887414f},
            {3191.48366296844234752f, -0.67424582104194355f, -0.80745813428471001f, -0.44925837484843441f,
                -0.35865440981033403f, -0.31322389111877305f, -0.37615025315725483f},
            {1157.50408145487200256f, -2.0531423165804414f, -1.4f, -0.50687130033378396f,
                -0.42708730624733904f, -1.4856834539296244f, -4.9209142884401604f},
        }, null, TransformType.MODE_DCT);
        defaultParams[5] = new DCTParams(new float[][]{
            {15718.40830982518931456f, -1.025f, -0.98f, -0.9012f, -0.4f, -0.48819395464f, -0.421064f, -0.27f},
            {7305.7636810695983104f, -0.8041958212306401f, -0.7633036457487539f, -0.55660379990111464f,
                -0.49785304658857626f, -0.43699592683512467f, -0.40180866526242109f, -0.27321683125358037f},
            {3803.53173721215041536f, -3.060733579805728f, -2.0413270132490346f, -2.0235650159727417f,
                -0.5495389509954993f, -0.4f, -0.4f, -0.3f},
        }, null, TransformType.MODE_DCT);
        defaultParams[6] = new DCTParams(new float[][]{
            {7240.7734393502f, -0.7f, -0.7f, -0.2f, -0.2f, -0.2f, -0.5f},
            {1448.15468787004f, -0.5f, -0.5f, -0.5f, -0.2f, -0.2f, -0.2f},
            {506.854140754517f, -1.4f, -0.2f, -0.5f, -0.5f, -1.5f, -3.6f},
        }, null, TransformType.MODE_DCT);
        defaultParams[7] = new DCTParams(new float[][]{
            {16283.2494710648897f, -1.7812845336559429f, -1.6309059012653515f,
                -1.0382179034313539f, -0.85f, -0.7f, -0.9f, -1.2360638576849587f},
            {5089.15750884921511936f, -0.320049391452786891f, -0.35362849922161446f,
                -0.30340000000000003f, -0.61f, -0.5f, -0.5f, -0.6f},
            {3397.77603275308720128f, -0.321327362693153371f, -0.34507619223117997f,
                -0.70340000000000003f, -0.9f, -1.0f, -1.0f, -1.1754605576265209f},
        }, null, TransformType.MODE_DCT);
        defaultParams[8] = new DCTParams(new float[][]{
            {13844.97076442300573f, -0.97113799999999995f, -0.658f, -0.42026f, -0.22712f, -0.2206f, -0.226f, -0.6f},
            {4798.964084220744293f, -0.61125308982767057f, -0.83770786552491361f, -0.79014862079498627f,
                -0.2692727459704829f, -0.38272769465388551f, -0.22924222653091453f, -0.20719098826199578f},
            {1807.236946760964614f, -1.2f, -1.2f, -0.7f, -0.7f, -0.7f, -0.4f, -0.5f},
        }, null, TransformType.MODE_DCT);
        float[][] dct4x8Params = {
            {2198.050556016380522f, -0.96269623020744692f, -0.76194253026666783f, -0.6551140670773547f},
            {764.3655248643528689f, -0.92630200888366945f, -0.9675229603596517f, -0.27845290869168118f},
            {527.107573587542228f, -1.4594385811273854f, -1.450082094097871593f, -1.5843722511996204f},
        };
        defaultParams[9] = new DCTParams(dct4x8Params, new float[][]{{1.0f}, {1.0f}, {1.0f}},
            TransformType.MODE_DCT4_8);
        defaultParams[10] = new DCTParams(dct4x8Params, new float[][]{
            {3072f, 3072f, 256f, 256f, 256f, 414f, 0.0f, 0.0f, 0.0f},
            {1024f, 1024f, 50.0f, 50.0f, 50.0f, 58f, 0.0f, 0.0f, 0.0f},
            {384f, 384f, 12.0f, 12.0f, 12.0f, 22f, -0.25f, -0.25f, -0.25f},
        }, dct4x4params, TransformType.MODE_AFV);
        float[] seqA = {-1.025f, -0.78f, -0.65012f, -0.19041574084286472f,
            -0.20819395464f, -0.421064f, -0.32733845535848671f};
        float[] seqB = {-0.3041958212306401f, -0.3633036457487539f, -0.35660379990111464f, -0.3443074455424403f,
            -0.33699592683512467f, -0.30180866526242109f, -0.27321683125358037f};
        float[] seqC = {-1.2f, -1.2f, -0.8f, -0.7f, -0.7f, -0.4f, -0.5f};
        defaultParams[11] = new DCTParams(new float[][]{
            prepend(23966.1665298448605f, seqA),
            prepend(8380.19148390090414f, seqB),
            prepend(4493.02378009847706f, seqC),
        }, null, TransformType.MODE_DCT);
        defaultParams[12] = new DCTParams(new float[][]{
            prepend(15358.89804933239925f, seqA),
            prepend(5597.360516150652990f, seqB),
            prepend(2919.961618960011210f, seqC),
        }, null, TransformType.MODE_DCT);
        defaultParams[13] = new DCTParams(new float[][]{
            prepend(47932.3330596897210f, seqA),
            prepend(16760.38296780180828f, seqB),
            prepend(8986.04756019695412f, seqC),
        }, null, TransformType.MODE_DCT);
        defaultParams[14] = new DCTParams(new float[][]{
            prepend(30717.796098664792f, seqA),
            prepend(11194.72103230130598f, seqB),
            prepend(5839.92323792002242f, seqC),
        }, null, TransformType.MODE_DCT);
        defaultParams[15] = new DCTParams(new float[][]{
            prepend(95864.6661193794420f, seqA),
            prepend(33520.76593560361656f, seqB),
            prepend(17972.09512039390824f, seqC),
        }, null, TransformType.MODE_DCT);
        defaultParams[16] = new DCTParams(new float[][]{
            prepend(61435.5921973295970f, seqA),
            prepend(24209.44206460261196f, seqB),
            prepend(12979.84647584004484f, seqC),
        }, null, TransformType.MODE_DCT);
    }

    private static void readDefaultWeights() throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(
            HFGlobal.class.getResourceAsStream("/default-weights-float.dat")));

        for (int i : FlowHelper.range(17)) {
            TransformType tt = Stream.of(TransformType.values())
                    .filter(t -> t.parameterIndex == i && !t.isVertical()).findFirst().get();
            for (int c = 0; c < 3; c++) {
                defaultWeights[i][c] = new float[tt.matrixHeight][tt.matrixWidth];
                for (int y = 0; y < tt.matrixHeight; y++) {
                    for (int x = 0; x < tt.matrixWidth; x++) {
                        defaultWeights[i][c][y][x] = in.readFloat();
                    }
                }
            }
        }

        in.close();
    }

    static {
        setupDefaultParams();

        try {
            readDefaultWeights();
        } catch (IOException ioe) {
            FunctionalHelper.sneakyThrow(ioe);
        }
    }

    public final DCTParams[] params;
    public final float[][][][] weights;
    public final int numHfPresets;

    public HFGlobal(Bitreader reader, Frame frame) throws IOException {
        boolean quantAllDefault = reader.readBool();
        Loggers loggers = frame.getLoggers();
        if (quantAllDefault) {
            loggers.log(Loggers.LOG_TRACE, "Using default params.");
            params = defaultParams;
        } else {
            loggers.log(Loggers.LOG_TRACE, "Using individual params.");
            params = new DCTParams[17];
            for (int i = 0; i < 17; i++)
                setupDCTParam(reader, frame, i);
        }

        weights = new float[17][3][][];

        for (int i = 0; i < 17; i++)
            generateWeights(i);

        numHfPresets = 1 + reader.readBits(MathHelper.ceilLog1p(frame.getNumGroups() - 1));
    }

    private void setupDCTParam(Bitreader reader, Frame frame, int index) throws IOException {
        int encodingMode = reader.readBits(3);
        Loggers loggers = frame.getLoggers();
        loggers.log(Loggers.LOG_TRACE, "Parameter %d, encoded as %d", index, encodingMode);
        TransformType.validateIndex(index, encodingMode);
        float[][] m;
        switch (encodingMode) {
            case TransformType.MODE_LIBRARY:
                params[index] = defaultParams[index];
                break;
            case TransformType.MODE_HORNUSS:
                m = new float[3][3];
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 3; x++) {
                        m[y][x] = 64f * reader.readF16();
                    }
                }
                params[index] = new DCTParams(null, m, encodingMode);
                break;
            case TransformType.MODE_DCT2:
                m = new float[3][6];
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 6; x++) {
                        m[y][x] = 64f * reader.readF16();
                    }
                }
                params[index] = new DCTParams(null, m, encodingMode);
                break;
            case TransformType.MODE_DCT4:
                m = new float[3][2];
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 2; x++) {
                        m[y][x] = 64f * reader.readF16();
                    }
                }
                params[index] = new DCTParams(readDCTParams(reader), m, encodingMode);
                break;
            case TransformType.MODE_DCT:
                params[index] = new DCTParams(readDCTParams(reader), null, encodingMode);
                break;
            case TransformType.MODE_RAW:
                float den = reader.readF16();
                loggers.log(Loggers.LOG_TRACE, "Raw denominator: %f, reciprocol %f", den, 1.0D / den);
                // SPEC: do not zero pad to byte here
                TransformType tt = Stream.of(TransformType.values())
                    .filter(t -> t.parameterIndex == index && !t.isVertical()).findFirst().get();
                ModularChannelInfo[] info = new ModularChannelInfo[3];
                info[0] = new ModularChannel(tt.matrixWidth, tt.matrixHeight, 0, 0);
                info[1] = new ModularChannel(tt.matrixWidth, tt.matrixHeight, 0, 0);
                info[2] = new ModularChannel(tt.matrixWidth, tt.matrixHeight, 0, 0);
                ModularStream stream = new ModularStream(reader, frame, 1 + 3 * frame.getNumLFGroups() + index, info);
                stream.decodeChannels(reader);
                m = new float[3][tt.matrixWidth * tt.matrixHeight];
                int[][][] b = stream.getDecodedBuffer();
                for (int c = 0; c < 3; c++) {
                    for (int y = 0; y < b[c].length; y++) {
                        for (int x = 0; x < b[c][y].length; x++) {
                            m[c][y * tt.matrixWidth + x] = b[c][y][x];
                        }
                    }
                }
                loggers.log(Loggers.LOG_TRACE, "weights: %s", (Object)m);
                params[index] = new DCTParams(null, m, encodingMode, den);
                break;
            case TransformType.MODE_AFV:
                m = new float[3][9];
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 9; x++) {
                        m[y][x] = reader.readF16();
                        if (x < 6)
                            m[y][x] *= 64f;
                    }
                }
                float[][] d = readDCTParams(reader);
                float[][] f = readDCTParams(reader);
                params[index] = new DCTParams(d, m, f, encodingMode);
                break;
            default:
                throw new IllegalStateException("Challenge complete how did we get here");
        }
    }

    private float[][] getAFVTransformWeights(int index, int c) throws InvalidBitstreamException {
        float[][] weights4x8 = getDCTQuantWeights(8, 4, params[index].dctParam[c]);
        float[][] weights4x4 = getDCTQuantWeights(4, 4, params[index].params4x4[c]);
        float low = 0.8517778890324296f;
        float high = 12.97166202570235f;
        float[] bands = new float[4];
        bands[0] = params[index].param[c][5];
        if (bands[0] < 0)
            throw new InvalidBitstreamException("Illegal negative band value");
        for (int i = 1; i < 4; i++) {
            bands[i] = bands[i - 1] * quantMult(params[index].param[c][i + 5]);
            if (bands[i] < 0)
                throw new InvalidBitstreamException("Illegal negative band value");
        }
        float[][] weight = new float[8][8];
        weight[0][0] = 1f;
        weight[1][0] = params[index].param[c][0];
        weight[0][1] = params[index].param[c][1];
        weight[2][0] = params[index].param[c][2];
        weight[0][2] = params[index].param[c][3];
        weight[2][2] = params[index].param[c][4];
        for (IntPoint p : FlowHelper.range2D(4, 4)) {
            if (p.x < 2 && p.y < 2)
                continue;
            weight[2 * p.y][2 * p.x] = interpolate(afvFreqs[p.y * 4 + p.x] - low, high - low + 1e-6f, bands);
        }

        for (IntPoint p : FlowHelper.range2D(8, 4)) {
            if (p.x == 0 && p.y == 0)
                continue;
            weight[2 * p.y + 1][p.x] = weights4x8[p.y][p.x];
        }
        for (IntPoint p : FlowHelper.range2D(4, 4)) {
            if (p.x == 0 && p.y == 0)
                continue;
            weight[2 * p.y][2 * p.x + 1] = weights4x4[p.y][p.x];
        }
        return weight;
    }

    private void generateWeights(int index) throws InvalidBitstreamException {
        TransformType tt = Stream.of(TransformType.values())
                    .filter(t -> t.parameterIndex == index && !t.isVertical()).findFirst().get();
        if (params[index] == defaultParams[index]) {
            for (int c = 0; c < 3; c++)
                weights[index][c] = defaultWeights[index][c];
            return;
        }
        for (int c = 0; c < 3; c++) {
            float[][] w;
            switch (params[index].mode) {
                case TransformType.MODE_DCT:
                    weights[index][c] = getDCTQuantWeights(tt.matrixWidth, tt.matrixHeight, params[index].dctParam[c]);
                    break;
                case TransformType.MODE_DCT4:
                    weights[index][c] = new float[8][8];
                    w = getDCTQuantWeights(4, 4, params[index].dctParam[c]);
                    for (IntPoint p : FlowHelper.range2D(8, 8)) {
                        weights[index][c][p.y][p.x] = w[p.y/2][p.x/2];
                    }
                    weights[index][c][1][0] /= params[index].param[c][0];
                    weights[index][c][0][1] /= params[index].param[c][0];
                    weights[index][c][1][1] /= params[index].param[c][1];
                    break;
                case TransformType.MODE_DCT2:
                    w = new float[8][8];
                    w[0][0] = 1f;
                    w[0][1] = w[1][0] = params[index].param[c][0];
                    w[1][1] = params[index].param[c][1];
                    for (IntPoint p : FlowHelper.range2D(2, 2)) {
                        w[p.y][p.x+2] = w[p.x+2][p.y] = params[index].param[c][2];
                        w[p.y+2][p.x+2] = params[index].param[c][3];
                    }
                    for (IntPoint p : FlowHelper.range2D(4, 4)) {
                        w[p.y][p.x+4] = w[p.x+4][p.y] = params[index].param[c][4];
                        w[p.y+4][p.x+4] = params[index].param[c][5];
                    }
                    weights[index][c] = w;
                    break;
                case TransformType.MODE_HORNUSS:
                    w = new float[8][8];
                    for (int y = 0; y < 8; y++)
                        Arrays.fill(w[y], params[index].param[c][0]);
                    w[1][1] = params[index].param[c][2];
                    w[0][1] = w[1][0] = params[index].param[c][1];
                    w[0][0] = 1f;
                    weights[index][c] = w;
                    break;
                case TransformType.MODE_DCT4_8:
                    weights[index][c] = new float[8][8];
                    w = getDCTQuantWeights(8, 4, params[index].dctParam[c]);
                    for (IntPoint p : FlowHelper.range2D(8, 8)) {
                        weights[index][c][p.y][p.x] = w[p.y/2][p.x];
                    }
                    weights[index][c][1][0] /= params[index].param[c][0];
                    break;
                case TransformType.MODE_AFV:
                    weights[index][c] = getAFVTransformWeights(index, c);
                    break;
                case TransformType.MODE_RAW:
                    weights[index][c] = new float[tt.matrixHeight][tt.matrixWidth];
                    for (int y = 0; y < tt.matrixHeight; y++) {
                        for (int x = 0; x < tt.matrixWidth; x++) {
                             weights[index][c][y][x] = params[index].param[c][y * tt.matrixWidth + x]
                                * params[index].denominator;
                        }
                    }
                    break;
                default:
                    throw new IllegalStateException("Challenge complete how did we get here");
            }
        }
        if (params[index].mode != TransformType.MODE_RAW) {
            for (int c = 0; c < 3; c++) {
                for (IntPoint p : FlowHelper.range2D(IntPoint.sizeOf(weights[index][c]))) {
                    if (weights[index][c][p.y][p.x] <= 0f || !Float.isFinite(weights[index][c][p.y][p.x]))
                        throw new InvalidBitstreamException("Negative or infinite weight: " + index + ", " + c);
                    weights[index][c][p.y][p.x] = 1f / weights[index][c][p.y][p.x];
                }
            }
        }
    }
}
