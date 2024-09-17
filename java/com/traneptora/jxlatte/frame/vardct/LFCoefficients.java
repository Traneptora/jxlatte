package com.traneptora.jxlatte.frame.vardct;

import java.io.IOException;
import java.util.Arrays;

import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.FrameFlags;
import com.traneptora.jxlatte.frame.FrameHeader;
import com.traneptora.jxlatte.frame.group.LFGroup;
import com.traneptora.jxlatte.frame.modular.ModularChannel;
import com.traneptora.jxlatte.frame.modular.ModularStream;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.InvalidBitstreamException;
import com.traneptora.jxlatte.util.ImageBuffer;
import com.traneptora.jxlatte.util.Point;

public class LFCoefficients {
    public final float[][][] dequantLFCoeff;
    public final int[][] lfIndex;
    public final Frame frame;

    public LFCoefficients(Bitreader reader, LFGroup parent, Frame frame, ImageBuffer[] lfBuffer) throws IOException {
        this.frame = frame;
        this.lfIndex = new int[parent.size.height][parent.size.width];
        FrameHeader header = frame.getFrameHeader();
        boolean adaptiveSmoothing = (header.flags &
            (FrameFlags.SKIP_ADAPTIVE_LF_SMOOTHING | FrameFlags.USE_LF_FRAME)) == 0;
        ModularChannel[] info = new ModularChannel[3];
        float[][][] dequantLFCoeff = new float[3][][];
        boolean subsampled = header.jpegUpsamplingY[0] != 0 || header.jpegUpsamplingY[1] != 0
            || header.jpegUpsamplingY[2] != 0 || header.jpegUpsamplingX[0] != 0
            || header.jpegUpsamplingX[1] != 0 || header.jpegUpsamplingX[2] != 0;
        if (adaptiveSmoothing && subsampled)
            throw new InvalidBitstreamException("Adaptive Smoothing is incompatible with subsampling");

        for (int i = 0; i < 3; i++) {
            int sizeY = parent.size.height >> header.jpegUpsamplingY[i];
            int sizeX = parent.size.width >> header.jpegUpsamplingX[i];
            info[Frame.cMap[i]] = new ModularChannel(sizeY, sizeX, header.jpegUpsamplingY[i],
                header.jpegUpsamplingX[i]);
            dequantLFCoeff[i] = new float[sizeY][sizeX];
        }

        if ((header.flags & FrameFlags.USE_LF_FRAME) != 0) {
            Point pos = frame.getLFGroupLocation(parent.lfGroupID);
            int pY = pos.y << 8;
            int pX = pos.x << 8;
            this.dequantLFCoeff = dequantLFCoeff;
            for (int c = 0; c < 3; c++) {
                lfBuffer[c].castToFloatIfInt(~(~0 << frame.globalMetadata.getBitDepthHeader().bitsPerSample));
                float[][] b = lfBuffer[c].getFloatBuffer();
                for (int y = 0; y < dequantLFCoeff[c].length; y++) {
                    System.arraycopy(b[pY + y], pX, dequantLFCoeff[c][y], 0, dequantLFCoeff[c][y].length);
                }
            }
            return;
        }

        int extraPrecision = reader.readBits(2);
        ModularStream lfQuantStream = new ModularStream(reader, frame, 1 + parent.lfGroupID, info);
        lfQuantStream.decodeChannels(reader);
        int[][][] lfQuant = lfQuantStream.getDecodedBuffer();
        lfQuantStream = null;
        float[] scaledDequant = frame.getLFGlobal().scaledDequant;
        for (int i = 0; i < 3; i++) {
            // lfQuant is in Y, X, B order
            int c = Frame.cMap[i];
            float sd = scaledDequant[i] / (1 << extraPrecision);
            for (int y = 0; y < lfQuant[c].length; y++) {
                final float[] dq = dequantLFCoeff[i][y];
                final int[] q = lfQuant[c][y];
                for (int x = 0; x < lfQuant[c][y].length; x++)
                    dq[x] = q[x] * sd;
            }
        }

        // chroma from luma
        if (!subsampled) {
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

        if (adaptiveSmoothing)
            this.dequantLFCoeff = adaptiveSmooth(dequantLFCoeff, scaledDequant);
        else
            this.dequantLFCoeff = dequantLFCoeff;

        populateLFIndex(parent, lfQuant);
    }

    private void populateLFIndex(LFGroup parent, int[][][] lfQuant) {
        HFBlockContext hfctx = frame.getLFGlobal().hfBlockCtx;
        for (int y = 0; y < parent.size.height; y++) {
            for (int x = 0; x < parent.size.width; x++)
                lfIndex[y][x] = getLFIndex(lfQuant, hfctx, y, x);
        }
    }

    private float[][][] adaptiveSmooth(final float[][][] coeff, final float[] scaledDequant) {
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

        return dequantLFCoeff;
    }

    private int getLFIndex(int[][][] lfQuant, HFBlockContext hfctx, int y, int x) {
        int[] index = new int[3];
        FrameHeader header = frame.getFrameHeader();
        for (int i = 0; i < 3; i++) {
            int sy = y >> header.jpegUpsamplingY[i];
            int sx = x >> header.jpegUpsamplingX[i];
            final int[] hft = hfctx.lfThresholds[i];
            for (int j = 0; j < hft.length; j++) {
                if (lfQuant[Frame.cMap[i]][sy][sx] > hft[j])
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
