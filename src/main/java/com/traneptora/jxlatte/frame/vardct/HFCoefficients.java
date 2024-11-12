package com.traneptora.jxlatte.frame.vardct;

import java.io.IOException;

import com.traneptora.jxlatte.color.OpsinInverseMatrix;
import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.FrameHeader;
import com.traneptora.jxlatte.frame.group.LFGroup;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.InvalidBitstreamException;
import com.traneptora.jxlatte.util.Dimension;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.Point;

public class HFCoefficients {

    private static final int[] coeffFreqCtx = {
        // the first number is unused
        // if it's ever used I want to throw an ArrayIndexOOBException
        -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,
        15, 15, 16, 16, 17, 17, 18, 18, 19, 19, 20, 20, 21, 21, 22, 22,
        23, 23, 23, 23, 24, 24, 24, 24, 25, 25, 25, 25, 26, 26, 26, 26,
        27, 27, 27, 27, 28, 28, 28, 28, 29, 29, 29, 29, 30, 30, 30, 30,
    };

    private static final int[] coeffNumNonzeroCtx = {
        // the first number is unused
        // if it's ever used I want to throw an ArrayIndexOOBException
        -1, 0, 31, 62, 62, 93, 93, 93, 93, 123, 123, 123, 123, 152, 152,
        152, 152, 152, 152, 152, 152, 180, 180, 180, 180, 180, 180, 180,
        180, 180, 180, 180, 180, 206, 206, 206, 206, 206, 206, 206, 206,
        206, 206, 206, 206, 206, 206, 206, 206, 206, 206, 206, 206, 206,
        206, 206, 206, 206, 206, 206, 206, 206, 206, 206,
    };

    public final int hfPreset;
    private final HFBlockContext hfctx;
    public final LFGroup lfg;
    public final int groupID;
    public float[][][] dequantHFCoeff;
    public int[][][] quantizedCoeffs;
    public final EntropyStream stream;
    public final Frame frame;
    public final Point[] blocks;
    public final Point groupPos;

    public HFCoefficients(int hfPreset, LFGroup lfg, int groupID,
        EntropyStream stream, Frame frame, Point[] blocks, Point groupPos) {
        this.hfPreset = hfPreset;
        this.lfg = lfg;
        this.groupID = groupID;
        this.stream = stream;
        this.frame = frame;
        this.blocks = blocks;
        this.groupPos = groupPos;
        this.hfctx = frame.getLFGlobal().hfBlockCtx;
        quantizedCoeffs = new int[3][][];
        dequantHFCoeff = new float[3][][];
    }

    public static HFCoefficients readHfCoefficients(Bitreader reader, Frame frame, int pass, int group)
            throws IOException {
        int bits = MathHelper.ceilLog1p(frame.getHFGlobal().numHfPresets - 1);
        int hfPreset = reader.readBits(bits);
        FrameHeader header = frame.getFrameHeader();
        int shift = header.passes.shift[pass];
        HFPass hfPass = frame.getHFPass(pass);
        Dimension size = frame.getGroupSize(group);
        int[][][] nonZeroes = new int[3][32][32];
        EntropyStream stream = new EntropyStream(hfPass.contextStream);
        LFGroup lfg = frame.getLFGroupForGroup(group);
        Point groupPos = frame.groupPosInLFGroup(lfg.lfGroupID, group);
        Point[] blocks = new Point[lfg.hfMetadata.blockList.length];
        groupPos.y <<= 5;
        groupPos.x <<= 5;
        HFCoefficients hfcoeff = new HFCoefficients(hfPreset, lfg, group, stream, frame, blocks, groupPos);
        HFBlockContext hfctx = hfcoeff.hfctx;
        int offset = 495 * hfctx.numClusters * hfPreset;
        for (int c = 0; c < 3; c++) {
            int sY = size.height >> header.jpegUpsamplingY[c];
            int sX = size.width >> header.jpegUpsamplingX[c];
            hfcoeff.quantizedCoeffs[c] = new int[sY][sX];
            hfcoeff.dequantHFCoeff[c] = new float[sY][sX];
        }
        for (int i = 0; i < lfg.hfMetadata.blockList.length; i++) {
            Point posInLfg = lfg.hfMetadata.blockList[i];
            int groupY = posInLfg.y - groupPos.y;
            int groupX = posInLfg.x - groupPos.x;
            if (groupY < 0 || groupX < 0 || groupY >= 32 || groupX >= 32) {
                // not in this group
                continue;
            }
            blocks[i] = posInLfg;
            TransformType tt = lfg.hfMetadata.dctSelect[posInLfg.y][posInLfg.x];
            boolean flip = tt.flip();
            int hfMult = lfg.hfMetadata.hfMultiplier[posInLfg.y][posInLfg.x];
            int lfIndex = lfg.lfCoeff.lfIndex[posInLfg.y][posInLfg.x];
            int numBlocks = tt.dctSelectHeight * tt.dctSelectWidth;
            for (final int c : Frame.cMap) {
                int sGroupY = groupY >> header.jpegUpsamplingY[c];
                int sGroupX = groupX >> header.jpegUpsamplingX[c];
                if (groupY != sGroupY << header.jpegUpsamplingY[c] || groupX != sGroupX << header.jpegUpsamplingX[c]) {
                    // subsampled block
                    continue;
                }
                int pixelGroupY = sGroupY << 3;
                int pixelGroupX = sGroupX << 3;
                int predicted = getPredictedNonZeroes(nonZeroes, c, sGroupY, sGroupX);
                int blockCtx = hfcoeff.getBlockContext(c, tt.orderID, hfMult, lfIndex);
                int nonZeroCtx = offset + hfcoeff.getNonZeroContext(predicted, blockCtx);
                int nonZero = stream.readSymbol(reader, nonZeroCtx);
                int[][] nz = nonZeroes[c];
                for (int iy = 0; iy < tt.dctSelectHeight; iy++) {
                    for (int ix = 0; ix < tt.dctSelectWidth; ix++) {
                        nz[sGroupY + iy][sGroupX + ix] = (nonZero + numBlocks - 1) / numBlocks;
                    }
                }
                // SPEC: spec doesn't say you abort here if nonZero == 0
                if (nonZero <= 0)
                    continue;
                int orderSize = hfPass.order[tt.orderID][c].length;
                int ucoeff = 0;
                int histCtx = offset + 458 * blockCtx + 37 * hfctx.numClusters;
                for (int k = 0; k < orderSize - numBlocks; k++) {
                    // SPEC: spec has this condition flipped
                    int prev = k == 0 ? (nonZero > (orderSize >> 4) ? 0 : 1) : (ucoeff != 0 ? 1 : 0);
                    int ctx = histCtx + hfcoeff.getCoefficientContext(k + numBlocks, nonZero, numBlocks, prev);
                    ucoeff = stream.readSymbol(reader, ctx);
                    Point order = hfPass.order[tt.orderID][c][k + numBlocks];
                    int posY = (flip ? order.x : order.y) + pixelGroupY;
                    int posX = (flip ? order.y : order.x) + pixelGroupX;
                    hfcoeff.quantizedCoeffs[c][posY][posX] = MathHelper.unpackSigned(ucoeff) << shift;
                    if (ucoeff != 0) {
                        if (--nonZero == 0)
                            break;
                    }                    
                }
                // SPEC: spec doesn't mention that nonZero > 0 is illegal
                if (nonZero != 0)
                    throw new InvalidBitstreamException(String.format(
                        "Illegal final nonzero count: %s, in group %d, at varblock (%d, %d, c=%d)" ,
                        nonZero, group, sGroupY, sGroupX, c));
            }
        }
        if (!stream.validateFinalState())
            throw new InvalidBitstreamException("Illegal final state in PassGroup: " + pass + ", " + group);

        return hfcoeff;
    }

    public void bakeDequantizedCoeffs() {
        dequantizeHFCoefficients();
        chromaFromLuma();
        finalizeLLF();
    }

    private void chromaFromLuma() {
        if (frame.getFrameHeader().isSubsampled)
            return;
        LFChannelCorrelation lfc = frame.getLFGlobal().lfChanCorr;
        int[][] xFactorHF = lfg.hfMetadata.hfStreamBuffer[0];
        int[][] bFactorHF = lfg.hfMetadata.hfStreamBuffer[1];
        float[][] xFactors = new float[xFactorHF.length][xFactorHF[0].length];
        float[][] bFactors = new float[bFactorHF.length][bFactorHF[0].length];
        for (int i = 0; i < blocks.length; i++) {
            Point pos = blocks[i];
            if (pos == null)
                continue;
            TransformType tt = lfg.hfMetadata.dctSelect[pos.y][pos.x];
            int pPosY = pos.y << 3;
            int pPosX = pos.x << 3;
            for (int iy = 0; iy < tt.pixelHeight; iy++) {
                int y = pPosY + iy;
                int fy = y >> 6;
                boolean by = (fy << 6) == y;
                float[] xF = xFactors[fy];
                float[] bF = bFactors[fy];
                int[] hfX = xFactorHF[fy];
                int[] hfB = bFactorHF[fy];
                for (int ix = 0; ix < tt.pixelWidth; ix++) {
                    int x = pPosX + ix;
                    int fx = x >> 6;
                    float kX;
                    float kB;
                    if (by && (fx << 6) == x) {
                        kX = lfc.baseCorrelationX + hfX[fx] / (float)lfc.colorFactor;
                        kB = lfc.baseCorrelationB + hfB[fx] / (float)lfc.colorFactor;
                        xF[fx] = kX;
                        bF[fx] = kB;
                    } else {
                        kX = xF[fx];
                        kB = bF[fx];
                    }
                    int pY = y & 0xFF;
                    int pX = x & 0xFF;
                    float dequantY = dequantHFCoeff[1][pY][pX];
                    dequantHFCoeff[0][pY][pX] += kX * dequantY;
                    dequantHFCoeff[2][pY][pX] += kB * dequantY;
                }
            }
        }
    }

    private void finalizeLLF() {
        float[][][] scratchBlock = new float[2][32][32];
        // put the LF coefficients into the HF coefficent array
        FrameHeader header = frame.getFrameHeader();
        for (int i = 0; i < blocks.length; i++) {
            Point posInLfg = blocks[i];
            if (posInLfg == null)
                continue;
            TransformType tt = lfg.hfMetadata.dctSelect[posInLfg.y][posInLfg.x];
            int groupY = posInLfg.y - groupPos.y;
            int groupX = posInLfg.x - groupPos.x;
            for (int c = 0; c < 3; c++) {
                int sGroupY = groupY >> header.jpegUpsamplingY[c];
                int sGroupX = groupX >> header.jpegUpsamplingX[c];
                if (groupY != sGroupY << header.jpegUpsamplingY[c] || groupX != sGroupX << header.jpegUpsamplingX[c]) {
                    // subsampled block
                    continue;
                }
                int pixelGroupY = sGroupY << 3;
                int pixelGroupX = sGroupX << 3;
                int sLfgY = posInLfg.y >> header.jpegUpsamplingY[c];
                int sLfgX = posInLfg.x >> header.jpegUpsamplingX[c];
                float[][] dqlf = lfg.lfCoeff.dequantLFCoeff[c];
                float[][] dq = dequantHFCoeff[c];
                if (tt.transformMethod != TransformType.METHOD_DCT) {
                    dq[pixelGroupY][pixelGroupX] = dqlf[sLfgY][sLfgX];
                    continue;
                }
                MathHelper.forwardDCT2D(dqlf, dq, new Point(sLfgY, sLfgX),
                    new Point(pixelGroupY, pixelGroupX), tt.getDctSelectSize(),
                    scratchBlock[0], scratchBlock[1]);
                for (int y = 0; y < tt.dctSelectHeight; y++) {
                    final float[] dqy = dq[y + pixelGroupY];
                    final float[] llfy = tt.llfScale[y];
                    for (int x = 0; x < tt.dctSelectWidth; x++)
                        dqy[x + pixelGroupX] *= llfy[x];
                }
            }
        }
    }

    private int getBlockContext(int c, int orderID, int hfMult, int lfIndex) {
        int idx = (c < 2 ? 1 - c : c) * 13 + orderID;
        idx *= hfctx.qfThresholds.length + 1;
        for (int t : hfctx.qfThresholds) {
            if (hfMult > t)
                idx++;
        }
        idx *= hfctx.numLFContexts;
        return hfctx.clusterMap[idx + lfIndex];
    }

    private int getNonZeroContext(int predicted, int ctx) {
        if (predicted > 64)
            predicted = 64;
        if (predicted < 8)
            return ctx + hfctx.numClusters * predicted;

        return ctx + hfctx.numClusters * (4 + predicted/2);
    }

    private int getCoefficientContext(int k, int nonZeroes, int numBlocks, int prev) {
        nonZeroes = (nonZeroes + numBlocks - 1) / numBlocks;
        k /= numBlocks;
        return (coeffNumNonzeroCtx[nonZeroes] + coeffFreqCtx[k]) * 2 + prev;
    }

    private static int getPredictedNonZeroes(int[][][] nonZeroes, int c, int y, int x) {
        if (x == 0 && y == 0)
            return 32;
        if (x == 0)
            return nonZeroes[c][y - 1][0];
        if (y == 0)
            return nonZeroes[c][0][x - 1];
        return (nonZeroes[c][y - 1][x] + nonZeroes[c][y][x - 1] + 1) >> 1;
    }

    private void dequantizeHFCoefficients() {
        OpsinInverseMatrix matrix = frame.globalMetadata.getOpsinInverseMatrix();
        FrameHeader header = frame.getFrameHeader();
        float globalScale = 65536.0f / frame.getLFGlobal().globalScale;
        float[] scaleFactor = new float[]{
            globalScale * (float)Math.pow(0.8D, header.xqmScale - 2D),
            globalScale,
            globalScale * (float)Math.pow(0.8D, header.bqmScale - 2D),
        };
        float[][][][] weights = frame.getHFGlobal().weights;
        float[][] qbclut = new float[][]{
            {-matrix.quantBias[0], 0.0f, matrix.quantBias[0]},
            {-matrix.quantBias[1], 0.0f, matrix.quantBias[1]},
            {-matrix.quantBias[2], 0.0f, matrix.quantBias[2]},
        };
        for (int i = 0; i < blocks.length; i++) {
            Point pos = blocks[i];
            if (pos == null)
                continue;
            TransformType tt = lfg.hfMetadata.dctSelect[pos.y][pos.x];
            int groupY = pos.y - groupPos.y;
            int groupX = pos.x - groupPos.x;
            boolean flip = tt.flip();
            float[][][] w2 = weights[tt.parameterIndex];
            for (int c = 0; c < 3; c++) {
                int sGroupY = groupY >> header.jpegUpsamplingY[c];
                int sGroupX = groupX >> header.jpegUpsamplingX[c];
                if (groupY != sGroupY << header.jpegUpsamplingY[c] || groupX != sGroupX << header.jpegUpsamplingX[c]) {
                    // subsampled block
                    continue;
                }
                float[][] w3 = w2[c];
                float sfc = scaleFactor[c] / lfg.hfMetadata.hfMultiplier[pos.y][pos.x];
                int pixelGroupY = sGroupY << 3;
                int pixelGroupX = sGroupX << 3;
                float[] qbc = qbclut[c];
                for (int y = 0; y < tt.pixelHeight; y++) {
                    for (int x = 0; x < tt.pixelWidth; x++) {
                        if (y < tt.dctSelectHeight && x < tt.dctSelectWidth)
                            continue;
                        int pY = pixelGroupY + y;
                        int pX = pixelGroupX + x;
                        int coeff = quantizedCoeffs[c][pY][pX];
                        float quant = (coeff > -2 && coeff < 2) ? qbc[coeff + 1] :
                            coeff - matrix.quantBiasNumerator / coeff;
                        int wy = flip ? x : y;
                        int wx = x ^ y ^ wy;
                        dequantHFCoeff[c][pY][pX] = quant * sfc * w3[wy][wx];
                    }
                }
            }
        }
    }
}
