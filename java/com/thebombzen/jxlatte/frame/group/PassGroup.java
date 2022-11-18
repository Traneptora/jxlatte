package com.thebombzen.jxlatte.frame.group;

import java.io.IOException;

import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.frame.vardct.HFCoefficients;
import com.thebombzen.jxlatte.frame.vardct.TransformType;
import com.thebombzen.jxlatte.frame.vardct.Varblock;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.FlowHelper;
import com.thebombzen.jxlatte.util.IntPoint;
import com.thebombzen.jxlatte.util.MathHelper;

public class PassGroup {

    private static final double[][] AFV_BASIS =  {{0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.25,
        0.25, 0.25, 0.25, 0.25, 0.25}, {0.876902929799142, 0.2206518106944235, -0.10140050393753763,
        -0.1014005039375375, 0.2206518106944236, -0.10140050393753777, -0.10140050393753772, -0.10140050393753763,
        -0.10140050393753758, -0.10140050393753769, -0.1014005039375375, -0.10140050393753768, -0.10140050393753768,
        -0.10140050393753759, -0.10140050393753763, -0.10140050393753741}, {0.0, 0.0, 0.40670075830260755,
        0.44444816619734445, 0.0, 0.0, 0.19574399372042936, 0.2929100136981264, -0.40670075830260716,
        -0.19574399372042872, 0.0, 0.11379074460448091, -0.44444816619734384, -0.29291001369812636,
        -0.1137907446044814, 0.0}, {0.0, 0.0, -0.21255748058288748, 0.3085497062849767, 0.0, 0.4706702258572536,
        -0.1621205195722993, 0.0, -0.21255748058287047, -0.16212051957228327, -0.47067022585725277,
        -0.1464291867126764, 0.3085497062849487, 0.0, -0.14642918671266536, 0.4251149611657548,}, {0.0,
        -0.7071067811865474, 0.0, 0.0, 0.7071067811865476, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
        {-0.4105377591765233, 0.6235485373547691, -0.06435071657946274, -0.06435071657946266, 0.6235485373547694,
        -0.06435071657946284, -0.0643507165794628, -0.06435071657946274, -0.06435071657946272, -0.06435071657946279,
        -0.06435071657946266, -0.06435071657946277, -0.06435071657946277, -0.06435071657946273, -0.06435071657946274,
        -0.0643507165794626}, {0.0, 0.0, -0.4517556589999482, 0.15854503551840063, 0.0, -0.04038515160822202,
        0.0074182263792423875, 0.39351034269210167, -0.45175565899994635, 0.007418226379244351, 0.1107416575309343,
        0.08298163094882051, 0.15854503551839705, 0.3935103426921022, 0.0829816309488214, -0.45175565899994796},
        {0.0, 0.0, -0.304684750724869, 0.5112616136591823, 0.0, 0.0, -0.290480129728998, -0.06578701549142804,
        0.304684750724884, 0.2904801297290076, 0.0, -0.23889773523344604, -0.5112616136592012, 0.06578701549142545,
        0.23889773523345467, 0.0}, {0.0, 0.0, 0.3017929516615495, 0.25792362796341184, 0.0, 0.16272340142866204,
        0.09520022653475037, 0.0, 0.3017929516615503, 0.09520022653475055, -0.16272340142866173, -0.35312385449816297,
        0.25792362796341295, 0.0, -0.3531238544981624, -0.6035859033230976,}, {0.0, 0.0, 0.40824829046386274, 0.0, 0.0,
        0.0, 0.0, -0.4082482904638628, -0.4082482904638635, 0.0, 0.0, -0.40824829046386296, 0.0, 0.4082482904638634,
        0.408248290463863, 0.0}, {0.0, 0.0, 0.1747866975480809, 0.0812611176717539, 0.0, 0.0, -0.3675398009862027,
        -0.307882213957909, -0.17478669754808135, 0.3675398009862011, 0.0, 0.4826689115059883, -0.08126111767175039,
        0.30788221395790305, -0.48266891150598584, 0.0},{0.0, 0.0, -0.21105601049335784, 0.18567180916109802, 0.0, 0.0,
        0.49215859013738733, -0.38525013709251915, 0.21105601049335806, -0.49215859013738905, 0.0, 0.17419412659916217,
        -0.18567180916109904, 0.3852501370925211, -0.1741941265991621, 0.0}, {0.0, 0.0, -0.14266084808807264,
        -0.3416446842253372, 0.0, 0.7367497537172237, 0.24627107722075148, -0.08574019035519306, -0.14266084808807344,
        0.24627107722075137, 0.14883399227113567, -0.04768680350229251, -0.3416446842253373, -0.08574019035519267,
        -0.047686803502292804, -0.14266084808807242}, {0.0, 0.0, -0.13813540350758585, 0.3302282550303788, 0.0,
        0.08755115000587084, -0.07946706605909573, -0.4613374887461511, -0.13813540350758294, -0.07946706605910261,
        0.49724647109535086, 0.12538059448563663, 0.3302282550303805, -0.4613374887461554, 0.12538059448564315,
        -0.13813540350758452}, {0.0, 0.0, -0.17437602599651067, 0.0702790691196284, 0.0, -0.2921026642334881,
        0.3623817333531167, 0.0, -0.1743760259965108, 0.36238173335311646, 0.29210266423348785, -0.4326608024727445,
        0.07027906911962818, 0.0, -0.4326608024727457, 0.34875205199302267,}, {0.0, 0.0, 0.11354987314994337,
        -0.07417504595810355, 0.0, 0.19402893032594343, -0.435190496523228, 0.21918684838857466, 0.11354987314994257,
        -0.4351904965232251, 0.5550443808910661, -0.25468277124066463, -0.07417504595810233, 0.2191868483885728,
        -0.25468277124066413, 0.1135498731499429}
    };

    public final HFCoefficients hfCoefficients;
    public final ModularStream stream;
    public final Frame frame;
    public final int groupID;
    public final int passID;
    public PassGroup(Bitreader reader, Frame frame, int pass, int group,
            ModularChannelInfo[] replacedChannels) throws IOException {
        this.frame = frame;
        this.groupID = group;
        this.passID = pass;
        if (frame.getFrameHeader().encoding == FrameFlags.VARDCT) {
            hfCoefficients = new HFCoefficients(reader, frame, pass, group);
        } else {
            hfCoefficients = null;
        }
        stream = new ModularStream(reader, frame,
            18 + 3*frame.getNumLFGroups() + frame.getNumGroups()*pass + group, replacedChannels);
        stream.decodeChannels(reader);
    }

    private void layBlock(double[][] block, double[][] buffer, IntPoint inPos, IntPoint outPos, IntPoint inSize, boolean transpose) {
        for (IntPoint p : FlowHelper.range2D(inSize)) {
            IntPoint pt = transpose ? p.transpose() : p;
            outPos.plus(pt).set(buffer, inPos.plus(p).get(block));
        }
    }

    private void invertAFV(double[][] coeffs, double[][] buffer, Varblock varblock, IntPoint pixelPosInFrame, double[][][] scratchBlock) {
        IntPoint p = IntPoint.ZERO;
        IntPoint ps = pixelPosInFrame;
        scratchBlock[0][0][0] = (coeffs[p.y][p.x] + coeffs[p.y + 1][p.x] + coeffs[p.y][p.x + 1]) * 4D;
        for (int iy = 0; iy < 4; iy++) {
            for (int ix = (iy == 0 ? 1 : 0); ix < 4; ix++) {
                scratchBlock[0][iy][ix] = coeffs[p.y + iy * 2][p.x + ix * 2];
            }
        }
        TransformType tt = varblock.transformType();
        int flipX = tt == TransformType.AFV1 || tt == TransformType.AFV3 ? 1 : 0;
        int flipY = tt == TransformType.AFV2 || tt == TransformType.AFV3 ? 1 : 0;
        for (int iy = 0; iy < 4; iy++) {
            for (int ix = 0; ix < 4; ix++) {
                double sample = 0D;
                for (int j = 0; j < 16; j++) {
                    sample += IntPoint.coordinates(j, 4).get(scratchBlock[0]) * AFV_BASIS[j][iy * 4 + ix];
                }
                scratchBlock[1][iy][ix] = sample;
            }
        }
        for (int iy = 0; iy < 4; iy++) {
            for (int ix = 0; ix < 4; ix++) {
                buffer[ps.y + flipY * 4 + iy][ps.x + flipX * 4 + ix] = scratchBlock[1][flipY == 1 ? 3 - iy : iy][flipX == 1 ? 3 - ix : ix];
            }
        }
        // SPEC: watch signs here
        scratchBlock[0][0][0] = coeffs[p.y][p.x] + coeffs[p.y + 1][p.x] - coeffs[p.y][p.x + 1];
        for (int iy = 0; iy < 4; iy++) {
            for (int ix = (iy == 0 ? 1 : 0); ix < 4; ix++) {
                scratchBlock[0][iy][ix] = coeffs[p.y + iy * 2][p.x + ix * 2 + 1];
            }
        }
        MathHelper.inverseDCT2D(scratchBlock[0], scratchBlock[1], IntPoint.ZERO, IntPoint.ZERO, new IntPoint(4), scratchBlock[2]);
        for (int iy = 0; iy < 4; iy++) {
            for (int ix = 0; ix < 4; ix++) {
                 //transposed intentionally
                buffer[ps.y + flipY * 4 + iy][ps.x + (flipX == 1 ? 0 : 4) + ix] = scratchBlock[1][ix][iy];
            }
        }
        // SPEC: wrong coefficient
        scratchBlock[0][0][0] = coeffs[p.y][p.x] - coeffs[p.y][p.x + 1];
        for (int iy = 0; iy < 4; iy++) {
            for (int ix = (iy == 0 ? 1 : 0); ix < 8; ix++) {
                scratchBlock[0][iy][ix] = coeffs[p.y + 1 + iy * 2][p.x + ix];
            }
        }
        MathHelper.inverseDCT2D(scratchBlock[0], scratchBlock[1], IntPoint.ZERO, IntPoint.ZERO, new IntPoint(8, 4), scratchBlock[2]);
        for (int iy = 0; iy < 4; iy++) {
            for (int ix = 0; ix < 8; ix++) {
                buffer[ps.y + (flipY == 1 ? 0 : 4) + iy][ps.x + ix] = scratchBlock[1][iy][ix];
            }
        }
    }

    private void auxDCT2(double[][] coeffs, double[][] result, IntPoint p, IntPoint ps, int s) {
        int num = s / 2;
        for (int iy = 0; iy < 4; iy++) {
            for (int ix = 0; ix < 4; ix++) {
                if (iy >= num || ix >= num) {
                    result[ps.y + iy * 2][ps.x + ix * 2] = coeffs[p.y + iy * 2][p.x + ix * 2];
                    result[ps.y + iy * 2 + 1][ps.x + ix * 2] = coeffs[p.y + iy * 2 + 1][p.x + ix * 2];
                    result[ps.y + iy * 2][ps.x + ix * 2 + 1] = coeffs[p.y + iy * 2][p.x + ix * 2 + 1];
                    result[ps.y + iy * 2 + 1][ps.x + ix * 2 + 1] = coeffs[p.y + iy * 2 + 1][p.x + ix * 2 + 1];
                    continue;
                }
                double c00 = coeffs[p.y + iy][p.x + ix];
                double c01 = coeffs[p.y + iy][p.x + ix + num];
                double c10 = coeffs[p.y + iy + num][p.x + ix];
                double c11 = coeffs[p.y + iy + num][p.x + ix + num];
                double r00 = c00 + c01 + c10 + c11;
                double r01 = c00 + c01 - c10 - c11;
                double r10 = c00 - c01 + c10 - c11;
                double r11 = c00 - c01 - c10 + c11;
                result[ps.y + iy * 2][ps.x + ix * 2] = r00;
                result[ps.y + iy * 2][ps.x + ix * 2 + 1] = r01;
                result[ps.y + iy * 2 + 1][ps.x + ix * 2] = r10;
                result[ps.y + iy * 2 + 1][ps.x + ix * 2 + 1] = r11;
            }
        }
    }

    public void invertVarDCT(double[][][] frameBuffer, PassGroup prev) {
        double[][][][] coeffs = hfCoefficients.dequantHFCoeff;
        if (prev != null) {
            for (int i = 0; i < hfCoefficients.varblocks.length; i++) {
                for (int c : Frame.cMap) {
                    for (IntPoint p : FlowHelper.range2D(IntPoint.sizeOf(coeffs[i][c]))) {
                        coeffs[i][c][p.y][p.x] += prev.hfCoefficients.dequantHFCoeff[i][c][p.y][p.x];
                    }
                }
            }
        }
        IntPoint[] shift = frame.getFrameHeader().jpegUpsampling;
        double[][][] scratchBlock = new double[3][256][256];
        for (int i = 0; i < hfCoefficients.varblocks.length; i++) {
            Varblock varblock = hfCoefficients.varblocks[i];
            for (int c = 0; c < 3; c++) {
                if (!varblock.isCorner(shift[c]))
                    continue;
                TransformType tt = varblock.transformType();
                IntPoint pixelPosInFrame = varblock.pixelPosInFrame.shiftRight(shift[c]);
                double coeff0, coeff1;
                double[] lfs = new double[2];
                IntPoint size = varblock.sizeInPixels();
                switch (tt.transformMethod) {
                    case TransformType.METHOD_DCT:
                        MathHelper.inverseDCT2D(coeffs[i][c], frameBuffer[c], IntPoint.ZERO, pixelPosInFrame, size, scratchBlock[0]);
                        break;
                    case TransformType.METHOD_DCT8_4:
                        coeff0 = coeffs[i][c][0][0];
                        coeff1 = coeffs[i][c][1][0];
                        lfs[0] = coeff0 + coeff1;
                        lfs[1] = coeff0 - coeff1;
                        for (int x = 0; x < 2; x++) {
                            scratchBlock[0][0][0] = lfs[x];
                            for (int iy = 0; iy < 4; iy++) {
                                for (int ix = (iy == 0 ? 1 : 0); ix < 8; ix++) {
                                    scratchBlock[0][iy][ix] = coeffs[i][c][x + iy * 2][ix];
                                }
                            }
                            MathHelper.inverseDCT2D(scratchBlock[0], scratchBlock[1], IntPoint.ZERO, new IntPoint(0, 4 * x), new IntPoint(8, 4), scratchBlock[2]);
                        }
                        layBlock(scratchBlock[1], frameBuffer[c], IntPoint.ZERO, pixelPosInFrame, size, true);
                        break;
                    case TransformType.METHOD_DCT4_8:
                        coeff0 = coeffs[i][c][0][0];
                        coeff1 = coeffs[i][c][1][0];
                        lfs[0] = coeff0 + coeff1;
                        lfs[1] = coeff0 - coeff1;
                        for (int y = 0; y < 2; y++) {
                            scratchBlock[0][0][0] = lfs[y];
                            for (int iy = 0; iy < 4; iy++) {
                                for (int ix = (iy == 0 ? 1 : 0); ix < 8; ix++) {
                                    scratchBlock[0][iy][ix] = coeffs[i][c][y + iy * 2][ix];
                                }
                            }
                            MathHelper.inverseDCT2D(scratchBlock[0], scratchBlock[1], IntPoint.ZERO, new IntPoint(0, 4 * y), new IntPoint(8, 4), scratchBlock[2]);
                        }
                        layBlock(scratchBlock[1], frameBuffer[c], IntPoint.ZERO, pixelPosInFrame, size, false);
                        break;
                    case TransformType.METHOD_AFV:
                        invertAFV(coeffs[i][c], frameBuffer[c], varblock, pixelPosInFrame, scratchBlock);
                        break;
                    case TransformType.METHOD_DCT2:
                        auxDCT2(coeffs[i][c], scratchBlock[0], IntPoint.ZERO, IntPoint.ZERO, 2);
                        auxDCT2(scratchBlock[0], scratchBlock[1], IntPoint.ZERO, IntPoint.ZERO, 4);
                        auxDCT2(scratchBlock[1], frameBuffer[c], IntPoint.ZERO, pixelPosInFrame, 8);
                        break;
                    case TransformType.METHOD_HORNUSS:
                        auxDCT2(coeffs[i][c], scratchBlock[1], IntPoint.ZERO, IntPoint.ZERO, 2);
                        for (int y = 0; y < 2; y++) {
                            for (int x = 0; x < 2; x++) {
                                double blockLF = scratchBlock[1][y][x];
                                double residual = 0D;
                                for (int iy = 0; iy < 4; iy++) {
                                    for (int ix = (iy == 0 ? 1 : 0); ix < 4; ix++) {
                                        residual += coeffs[i][c][y + iy * 2][x + ix * 2];
                                    }
                                }
                                scratchBlock[0][4 * y + 1][4 * x + 1] = blockLF - residual / 16D;
                                for (int iy = 0; iy < 4; iy++) {
                                    for (int ix = 0; ix < 4; ix++) {
                                        if (ix == 1 && iy == 1)
                                            continue;
                                        scratchBlock[0][y * 4 + iy][x * 4 + ix] = coeffs[i][c][y + iy * 2][x + ix * 2]
                                            + scratchBlock[0][4 * y + 1][4 * x + 1];

                                    }
                                }
                                scratchBlock[0][4 * y][4 * x] = coeffs[i][c][y + 2][x + 2]
                                    + scratchBlock[0][4 * y + 1][4 * x + 1];
                            }
                        }
                        layBlock(scratchBlock[0], frameBuffer[c], IntPoint.ZERO, pixelPosInFrame, size, false);
                        break;
                    case TransformType.METHOD_DCT4:
                        auxDCT2(coeffs[i][c], scratchBlock[1], IntPoint.ZERO, IntPoint.ZERO, 2);
                        for (int y = 0; y < 2; y++) {
                            for (int x = 0; x < 2; x++) {
                                scratchBlock[0][0][0] = scratchBlock[1][y][x];
                                for (int iy = 0; iy < 4; iy++) {
                                    for (int ix = (iy == 0 ? 1 : 0); ix < 4; ix++) {
                                        scratchBlock[0][iy][ix] = coeffs[i][c][x + ix * 2][y + iy * 2];
                                    }
                                }
                                // we're already using scratchblock[1] for the auxDCT2 coordiantes
                                // but we're putting these far away at (8, 8) so there's no overlap
                                MathHelper.inverseDCT2D(scratchBlock[0], scratchBlock[1], IntPoint.ZERO, new IntPoint(8, 8), new IntPoint(4, 4), scratchBlock[2]);
                                for (int iy = 0; iy < 4; iy++) {
                                    for (int ix = 0; ix < 4; ix++) {
                                        frameBuffer[c][pixelPosInFrame.y + 4* y + iy][pixelPosInFrame.x + 4*x + ix] = scratchBlock[1][8 + iy][8 + ix];
                                    }
                                }
                            }
                        }
                        break;
                    default:
                        throw new UnsupportedOperationException("Transform not implemented: " + tt);
                }
            }
        }
    }
}
