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
import com.thebombzen.jxlatte.util.IntPoint;

public class LFCoefficients {
    public final float[][][] dequantLFCoeff;
    public final int[][] lfIndex;
    public final Frame frame;

    public LFCoefficients(Bitreader reader, LFGroup parent, Frame frame, float[][][] lfBuffer) throws IOException {
        this.frame = frame;
        this.lfIndex = new int[parent.size.y][parent.size.x];
        if ((frame.getFrameHeader().flags & FrameFlags.USE_LF_FRAME) != 0) {
            this.dequantLFCoeff = lfBuffer;
            populateLFIndex(parent, null);
            return;
        }

        final IntPoint sizeInPixels = frame.getLFGroupSize(parent.lfGroupID);
        final IntPoint sizeInBlocks = sizeInPixels.shiftRight(3);
        final int extraPrecision = reader.readBits(2);
        final FrameHeader header = frame.getFrameHeader();
        final ModularChannelInfo[] info = new ModularChannelInfo[3];
        final float[][][] dequantLFCoeff = new float[3][][];
        final boolean adaptiveSmoothing = (header.flags
            & (FrameFlags.SKIP_ADAPTIVE_LF_SMOOTHING | FrameFlags.USE_LF_FRAME)) == 0;
        final IntPoint[] shift = header.jpegUpsampling;
        for (int i = 0; i < 3; i++) {
            if (adaptiveSmoothing && (shift[i].x != 0 || shift[i].y != 0))
                throw new InvalidBitstreamException("Adaptive Smoothing is incompatible with subsampling");
            final IntPoint channelSize = sizeInBlocks.shiftRight(shift[i]);
            info[Frame.cMap[i]] = new ModularChannelInfo(channelSize.x, channelSize.y, shift[i].x, shift[i].y);
            dequantLFCoeff[i] = new float[channelSize.y][channelSize.x];
        }
        ModularStream lfQuantStream = new ModularStream(reader, frame, 1 + parent.lfGroupID, info);
        lfQuantStream.decodeChannels(reader);
        final int[][][] lfQuant = lfQuantStream.getDecodedBuffer();
        lfQuantStream = null;
        final float[] scaledDequant = frame.getLFGlobal().quantizer.scaledDequant;
        for (int i = 0; i < 3; i++) {
            // quant is in Y, X, B order
            final int c = Frame.cMap[i];
            final float sd = scaledDequant[c] / (1 << extraPrecision);
            for (int y = 0; y < lfQuant[i].length; y++) {
                final float[] dq = dequantLFCoeff[c][y];
                final int[] q = lfQuant[i][y];
                for (int x = 0; x < lfQuant[i][y].length; x++)
                    dq[x] = q[x] * sd;
            }
        }
        if (adaptiveSmoothing)
            this.dequantLFCoeff = adaptiveSmooth(dequantLFCoeff, scaledDequant, header.jpegUpsampling);
        else
            this.dequantLFCoeff = dequantLFCoeff;
        populateLFIndex(parent, lfQuant);
    }

    private void populateLFIndex(final LFGroup parent, final int[][][] lfQuant) {
        final HFBlockContext hfctx = frame.getLFGlobal().hfBlockCtx;
        for (int y = 0; y < parent.size.y; y++) {
            final int[] lfi = lfIndex[y];
            for (int x = 0; x < parent.size.x; x++)
                lfi[x] = getLFIndex(lfQuant, hfctx, new IntPoint(x, y), frame.getFrameHeader().jpegUpsampling);
        }
    }

    private float[][][] adaptiveSmooth(final float[][][] coeff, final float[] scaledDequant, final IntPoint[] shift) {
        final float[][][] weighted = new float[3][][];
        final float[][] gap = new float[coeff[0].length][];
        final float[][][] dequantLFCoeff = new float[3][][];
        for (int i = 0; i < 3; i++) {
            final float[][] co = coeff[i];
            weighted[i] = new float[co.length][];
            final float sd = scaledDequant[i];
            for (int y = 1; y < co.length - 1; y++) {
                final float[] coy = co[y];
                final float[] coym = co[y - 1];
                final float[] coyp = co[y + 1];
                if (gap[y] == null) {
                    gap[y] = new float[coy.length];
                    Arrays.fill(gap[y], 0.5f);
                }
                final float[] gy = gap[y];
                // we never use weighted[i][0] so it can stay null
                weighted[i][y] = new float[coy.length];
                final float[] wy = weighted[i][y];
                for (int x = 1; x < coy.length - 1; x++) {
                    final float sample = coy[x];
                    final float adjacent = coy[x - 1] + coy[x + 1]
                        + coym[x] + coyp[x];
                    final float diag = coym[x - 1] + coym[x + 1]
                        + coyp[x - 1] + coyp[x + 1];
                    wy[x] = 0.05226273532324128f * sample + 0.20345139757231578f * adjacent
                        + 0.0334829185968739f * diag;
                    final float g = Math.abs(sample - wy[x]) * sd;
                    if (g > gy[x])
                        gy[x] = g;
                }
            }
        }
        for (int y = 0; y < gap.length; y++) {
            if (gap[y] == null)
                continue;
            final float[] gy = gap[y];
            for (int x = 0; x < gy.length; x++)
                gy[x] = Math.max(0f, 3f - 4f * gy[x]);
        }
        for (int i = 0; i < 3; i++) {
            final float[][] co = coeff[i];
            dequantLFCoeff[i] = new float[co.length][];
            final float[][] dqi = dequantLFCoeff[i];
            final float[][] wi = weighted[i];
            for (int y = 0; y < co.length; y++) {
                final float[] coy = co[y];
                dqi[y] = new float[coy.length];
                final float[] dqy = dqi[y];
                final float[] gy = gap[y];
                final float[] wiy = wi[y];
                if (y == 0 || y + 1 == co.length) {
                    System.arraycopy(coy, 0, dqy, 0, coy.length);
                    continue;
                }
                for (int x = 0; x < coy.length; x++) {
                    if (x == 0 || x + 1 == coy.length) {
                        dqy[x] = coy[x];
                        continue;
                    }
                    dqy[x] = (coy[x] - wiy[x]) * gy[x] + wiy[x];
                }
            }
        }
        // chroma from luma
        if (shift[0].x + shift[1].x + shift[2].x + shift[0].y + shift[1].y + shift[2].y == 0) {
            final LFChannelCorrelation lfc = frame.getLFGlobal().lfChanCorr;
            // SPEC: -128, not -127
            final float kX = lfc.baseCorrelationX + (lfc.xFactorLF - 128f) / (float)lfc.colorFactor;
            final float kB = lfc.baseCorrelationB + (lfc.bFactorLF - 128f) / (float)lfc.colorFactor;
            final float[][] dqLFY = dequantLFCoeff[1];
            final float[][] dqLFX = dequantLFCoeff[0];
            final float[][] dqLFB = dequantLFCoeff[2];
            for (int y = 0; y < dqLFY.length; y++) {
                final float[] dqLFYy = dqLFY[y];
                final float[] dqLFXy = dqLFX[y];
                final float[] dqLFBy = dqLFB[y];
                for (int x = 0; x < dqLFYy.length; x++) {
                    dqLFXy[x] += kX * dqLFYy[x];
                    dqLFBy[x] += kB * dqLFYy[x];
                }
            }
        }
        return dequantLFCoeff;
    }

    private int getLFIndex(final int[][][] lfQuant, final HFBlockContext hfctx,
            final IntPoint blockPos, final IntPoint[] upsampling) {
        final int[] index = new int[3];

        for (int i = 0; i < 3; i++) {
            final IntPoint shifted = blockPos.shiftRight(upsampling[i]);
            final int[] hft = hfctx.lfThresholds[i];
            for (int j = 0; j < hft.length; j++) {
                if (lfQuant[Frame.cMap[i]][shifted.y][shifted.x] > hft[j])
                    index[i]++;
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
