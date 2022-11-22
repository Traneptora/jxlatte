package com.thebombzen.jxlatte.frame.vardct;

import java.io.IOException;
import java.util.Arrays;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.FrameHeader;
import com.thebombzen.jxlatte.frame.group.LFGroup;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.FlowHelper;
import com.thebombzen.jxlatte.util.IntPoint;

public class LFCoefficients {
    public final int extraPrecision;
    public final float[][][] dequantLFCoeff;
    public final int[][] lfIndex;
    public final Frame frame;

    public LFCoefficients(Bitreader reader, LFGroup parent, Frame frame) throws IOException {
        this.frame = frame;
        if ((frame.getFrameHeader().flags & FrameFlags.USE_LF_FRAME) != 0) {
            System.err.println("TODO: Implement LF Frames");
            throw new UnsupportedOperationException("LF Frames currently not implemented");
        }
        IntPoint sizeInPixels = frame.getLFGroupSize(parent.lfGroupID);
        IntPoint sizeInBlocks = sizeInPixels.shiftRight(3);
        this.extraPrecision = reader.readBits(2);
        FrameHeader header = frame.getFrameHeader();
        ModularChannelInfo[] info = new ModularChannelInfo[3];
        float[][][] dequantLFCoeff = new float[3][][];
        boolean adaptiveSmoothing = (header.flags
            & (FrameFlags.SKIP_ADAPTIVE_LF_SMOOTHING | FrameFlags.USE_LF_FRAME)) == 0;
        IntPoint[] shift = header.jpegUpsampling;
        for (int i = 0; i < 3; i++) {
            if (adaptiveSmoothing && !shift[i].equals(IntPoint.ZERO))
                throw new InvalidBitstreamException("Adaptive Smoothing is incompatible with subsampling");
            IntPoint channelSize = sizeInBlocks.shiftRight(shift[i]);
            info[Frame.cMap[i]] = new ModularChannelInfo(channelSize.x, channelSize.y, shift[i].x, shift[i].y);
            dequantLFCoeff[i] = new float[channelSize.y][channelSize.x];
        }
        ModularStream lfQuantStream = new ModularStream(reader, frame, 1 + parent.lfGroupID, info);
        lfQuantStream.decodeChannels(reader);
        int[][][] lfQuant = lfQuantStream.getDecodedBuffer();
        lfQuantStream = null;
        float[] scaledDequant = frame.getLFGlobal().quantizer.scaledDequant;
        for (int i = 0; i < 3; i++) {
            // quant is in Y, X, B order
            int c = Frame.cMap[i];
            for (int y = 0; y < lfQuant[i].length; y++) {
                for (int x = 0; x < lfQuant[i][y].length; x++) {
                    dequantLFCoeff[c][y][x] = lfQuant[i][y][x] * scaledDequant[c] / (1 << extraPrecision);
                }
            }
        }
        if (adaptiveSmoothing) {
            this.dequantLFCoeff = adaptiveSmooth(dequantLFCoeff, scaledDequant, header.jpegUpsampling);
        } else {
            this.dequantLFCoeff = dequantLFCoeff;
        }
        this.lfIndex = new int[parent.size.y][parent.size.x];
        HFBlockContext hfctx = frame.getLFGlobal().hfBlockCtx;
        for (int y = 0; y < parent.size.y; y++) {
            for (int x = 0; x < parent.size.x; x++) {
                lfIndex[y][x] = getLFIndex(lfQuant, hfctx, new IntPoint(x, y), frame.getFrameHeader().jpegUpsampling);
            }
        }
    }

    private float[][][] adaptiveSmooth(float[][][] coeff, float[] scaledDequant, IntPoint[] shift) {
        float[][][] weighted = new float[3][][];
        float[][] gap = new float[coeff[0].length][];
        float[][][] dequantLFCoeff = new float[3][][];
        for (int i = 0; i < 3; i++) {
            weighted[i] = new float[coeff[i].length][];
            for (int y = 1; y < coeff[i].length - 1; y++) {
                if (gap[y] == null) {
                    gap[y] = new float[coeff[i][y].length];
                    Arrays.fill(gap[y], 0.5f);
                }
                // we never use weighted[i][0] so it can stay null
                weighted[i][y] = new float[coeff[i][y].length];
                for (int x = 1; x < coeff[i][y].length - 1; x++) {
                    float sample = coeff[i][y][x];
                    float adjacent = coeff[i][y][x - 1] + coeff[i][y][x + 1]
                        + coeff[i][y - 1][x] + coeff[i][y + 1][x];
                    float diag = coeff[i][y - 1][x - 1] + coeff[i][y - 1][x + 1]
                        + coeff[i][y + 1][x - 1] + coeff[i][y + 1][x + 1];
                    weighted[i][y][x] = 0.05226273532324128f * sample + 0.20345139757231578f * adjacent
                        + 0.0334829185968739f * diag;
                    float g = Math.abs(sample - weighted[i][y][x]) * scaledDequant[i];
                    if (g > gap[y][x])
                        gap[y][x] = g;
                }
            }
        }
        for (int y = 0; y < gap.length; y++) {
            if (gap[y] == null)
                continue;
            for (int x = 0; x < gap[y].length; x++) {
                gap[y][x] = Math.max(0f, 3f - 4f * gap[y][x]);
            }
        }
        for (int i = 0; i < 3; i++) {
            dequantLFCoeff[i] = new float[coeff[i].length][];
            for (int y = 0; y < coeff[i].length; y++) {
                dequantLFCoeff[i][y] = new float[coeff[i][y].length];
                if (y == 0 || y + 1 == coeff[i].length) {
                    System.arraycopy(coeff[i][y], 0, dequantLFCoeff[i][y], 0, coeff[i][y].length);
                    continue;
                }
                for (int x = 0; x < coeff[i][y].length; x++) {
                    if (x == 0 || x + 1 == coeff[i][y].length) {
                        dequantLFCoeff[i][y][x] = coeff[i][y][x];
                        continue;
                    }
                    dequantLFCoeff[i][y][x] = (coeff[i][y][x] - weighted[i][y][x]) * gap[y][x]
                        + weighted[i][y][x];
                }
            }
        }
        // chroma from luma
        if (shift[0].plus(shift[1]).plus(shift[2]).equals(IntPoint.ZERO)) {
            LFChannelCorrelation lfc = frame.getLFGlobal().lfChanCorr;
            // SPEC: -128, not -127
            float kX = lfc.baseCorrelationX + (lfc.xFactorLF - 128f) / (float)lfc.colorFactor;
            float kB = lfc.baseCorrelationB + (lfc.bFactorLF - 128f) / (float)lfc.colorFactor;
            for (IntPoint p : FlowHelper.range2D(IntPoint.sizeOf(dequantLFCoeff[1]))) {
                dequantLFCoeff[0][p.y][p.x] += kX * dequantLFCoeff[1][p.y][p.x];
                dequantLFCoeff[2][p.y][p.x] += kB * dequantLFCoeff[1][p.y][p.x];
            }
        }
        return dequantLFCoeff;
    }

    private int getLFIndex(int[][][] lfQuant, HFBlockContext hfctx, IntPoint blockPos, IntPoint[] upsampling) {
        int[] index = new int[3];

        for (int i = 0; i < 3; i++) {
            IntPoint shifted = blockPos.shiftLeft(upsampling[i].negate());
            for (int t : hfctx.lfThresholds[i]) {
                if (lfQuant[Frame.cMap[i]][shifted.y][shifted.x] > t) {
                    index[i]++;
                }
            }
        }

        int lfIndex = index[0];
        lfIndex *= hfctx.lfThresholds[2].length + 1;
        lfIndex += index[2];
        lfIndex *= hfctx.lfThresholds[1].length + 1;
        lfIndex += index[1];

        return lfIndex;
    }
}
