package com.traneptora.jxlatte.frame.vardct;

import java.io.IOException;
import java.util.Arrays;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.color.OpsinInverseMatrix;
import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.group.LFGroup;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.IntPoint;
import com.traneptora.jxlatte.util.MathHelper;

public class HFCoefficients {

    private static final int[] coeffFreqCtx = {
        // the first number is unused
        // if it's ever used I want to throw an ArrayIndexOOBException
        -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,
        15, 15, 16, 16, 17, 17, 18, 18, 19, 19, 20, 20, 21, 21, 22, 22,
        23, 23, 23, 23, 24, 24, 24, 24, 25, 25, 25, 25, 26, 26, 26, 26,
        27, 27, 27, 27, 28, 28, 28, 28, 29, 29, 29, 29, 30, 30, 30, 30
    };

    private static final int[] coeffNumNonzeroCtx = {
        // SPEC: spec has one of these as 23 rather than 123

        // the first number is unused
        // if it's ever used I want to throw an ArrayIndexOOBException
        -1, 0, 31, 62, 62, 93, 93, 93, 93, 123, 123, 123, 123, 152, 152,
        152, 152, 152, 152, 152, 152, 180, 180, 180, 180, 180, 180, 180,
        180, 180, 180, 180, 180, 206, 206, 206, 206, 206, 206, 206, 206,
        206, 206, 206, 206, 206, 206, 206, 206, 206, 206, 206, 206, 206,
        206, 206, 206, 206, 206, 206, 206, 206, 206, 206
    };

    public final int hfPreset;
    private final HFBlockContext hfctx;
    public final LFGroup lfg;
    public final int groupID;
    private final int[][][] nonZeroes;
    public final float[][][] dequantHFCoeff;
    public final int[][][] quantizedCoeffs;
    public final EntropyStream stream;
    public final Frame frame;
    public final Varblock[] varblocks;

    public HFCoefficients(Bitreader reader, Frame frame, int pass, int group) throws IOException {
        hfPreset = reader.readBits(MathHelper.ceilLog1p(frame.getHFGlobal().numHfPresets - 1));
        this.groupID = group;
        this.frame = frame;
        this.hfctx = frame.getLFGlobal().hfBlockCtx;
        this.lfg = frame.getLFGroupForGroup(group);
        final int offset = 495 * hfctx.numClusters * hfPreset;
        final HFPass hfPass = frame.getHFPass(pass);
        final IntPoint groupSize = frame.groupSize(group);
        final IntPoint groupBlockSize = groupSize.shiftRight(3);
        nonZeroes = new int[3][groupBlockSize.y][groupBlockSize.x];
        final IntPoint[] shift = frame.getFrameHeader().jpegUpsampling;
        stream = new EntropyStream(hfPass.contextStream);
        quantizedCoeffs = new int[3][][];
        dequantHFCoeff = new float[3][][];
        for (int c = 0; c < 3; c++) {
            IntPoint s = groupSize.shiftRight(shift[c]);
            quantizedCoeffs[c] = new int[s.y][s.x];
            dequantHFCoeff[c] = new float[s.y][s.x];
        }
        varblocks = new Varblock[lfg.hfMetadata.blockList.length];
        for (int i = 0; i < lfg.hfMetadata.blockList.length; i++) {
            final Varblock varblock = lfg.hfMetadata.getVarblock(i);
            if (varblock.groupID != groupID)
                continue; // block is not in this group
            varblocks[i] = varblock;
            final TransformType tt = varblock.transformType();
            final boolean flip = tt.flip();
            final int hfMult = varblock.hfMult();
            final int lfIndex = lfg.lfCoeff.lfIndex[varblock.blockPosInLFGroup.y][varblock.blockPosInLFGroup.x];
            final IntPoint sizeInBlocks = varblock.sizeInBlocks();
            final int numBlocks = sizeInBlocks.x * sizeInBlocks.y;
            for (final int c : Frame.cMap) {
                final IntPoint shiftC = shift[c];
                if (!varblock.isCorner(shiftC))
                    continue; // subsampled block
                final IntPoint shiftedBlockPos = varblock.blockPosInGroup.shiftRight(shiftC);
                final IntPoint ppg = shiftedBlockPos.shiftLeft(3);
                final int predicted = getPredictedNonZeroes(c, shiftedBlockPos);
                final int blockCtx = getBlockContext(c, tt.orderID, hfMult, lfIndex);
                final int nonZeroCtx = offset + getNonZeroContext(predicted, blockCtx);
                int nonZero = stream.readSymbol(reader, nonZeroCtx);
                final int[][] nz = nonZeroes[c];
                for (int iy = 0; iy < sizeInBlocks.y; iy++) {
                    for (int ix = 0; ix < sizeInBlocks.x; ix++) {
                        nz[shiftedBlockPos.y + iy][shiftedBlockPos.x + ix]
                            = (nonZero + numBlocks - 1) / numBlocks;
                    }
                }
                // SPEC: spec doesn't say you abort here if nonZero == 0
                if (nonZero <= 0)
                    continue;
                final int size = hfPass.order[tt.orderID][c].length;
                final int[] ucoeff = new int[size - numBlocks];
                final int histCtx = offset + 458 * blockCtx + 37 * hfctx.numClusters;
                for (int k = 0; k < ucoeff.length; k++) {
                    // SPEC: spec has this condition flipped
                    final int prev = k == 0 ? (nonZero > size/16 ? 0 : 1) : (ucoeff[k - 1] != 0 ? 1 : 0);
                    final int ctx = histCtx + getCoefficientContext(k + numBlocks, nonZero, numBlocks, prev);
                    ucoeff[k] = stream.readSymbol(reader, ctx);
                    final IntPoint pos = hfPass.order[tt.orderID][c][k + numBlocks];
                    final int posX = (flip ? pos.y : pos.x) + ppg.x;
                    final int posY = (flip ? pos.x : pos.y) + ppg.y;
                    quantizedCoeffs[c][posY][posX] = MathHelper.unpackSigned(ucoeff[k]);
                    if (ucoeff[k] != 0) {
                        if (--nonZero == 0)
                            break;
                    }                    
                }
                // SPEC: spec doesn't mention that nonZero > 0 is illegal
                if (nonZero != 0)
                    throw new InvalidBitstreamException(String.format(
                        "Illegal final nonzero count: %s, in group %d, at varblock (%d, %d, c=%d)" ,
                        nonZero, groupID, shiftedBlockPos.x, shiftedBlockPos.y, c));
            }
        }
        if (!stream.validateFinalState())
            throw new InvalidBitstreamException("Illegal final state in PassGroup: " + pass + ", " + group);

    }

    public void bakeDequantizedCoeffs() {
        dequantizeHFCoefficients();
        final IntPoint[] shift = frame.getFrameHeader().jpegUpsampling;
        // chroma from luma
        if (!Arrays.stream(shift).allMatch(x -> IntPoint.ZERO.equals(x)))
            return;
        final LFChannelCorrelation lfc = frame.getLFGlobal().lfChanCorr;
        final int[][] xFactorHF = lfg.hfMetadata.hfStreamBuffer[0];
        final int[][] bFactorHF = lfg.hfMetadata.hfStreamBuffer[1];
        final float[][] xFactors = new float[xFactorHF.length][xFactorHF[0].length];
        final float[][] bFactors = new float[bFactorHF.length][bFactorHF[0].length];
        for (int i = 0; i < varblocks.length; i++) {
            final Varblock varblock = varblocks[i];
            if (varblock == null)
                continue;
            final IntPoint sizeInPixels = varblock.sizeInPixels();
            for (int iy = 0; iy < sizeInPixels.y; iy++) {
                final int y = varblock.pixelPosInLFGroup.y + iy;
                final int fy = y >> 6;
                final boolean by = fy << 6 == y;
                final float[] xF = xFactors[fy];
                final float[] bF = bFactors[fy];
                final int[] hfX = xFactorHF[fy];
                final int[] hfB = bFactorHF[fy];
                for (int ix = 0; ix < sizeInPixels.x; ix++) {
                    final int x = varblock.pixelPosInLFGroup.x + ix;
                    final int fx = x >> 6;
                    final float kX;
                    final float kB;
                    if (by && fx << 6 == x) {
                        kX = lfc.baseCorrelationX + hfX[fx] / (float)lfc.colorFactor;
                        kB = lfc.baseCorrelationB + hfB[fx] / (float)lfc.colorFactor;
                        xF[fx] = kX;
                        bF[fx] = kB;
                    } else {
                        kX = xF[fx];
                        kB = bF[fx];
                    }
                    final float dequantY =
                        dequantHFCoeff[1][varblock.pixelPosInGroup.y + iy][varblock.pixelPosInGroup.x + ix];
                    dequantHFCoeff[0][varblock.pixelPosInGroup.y + iy][varblock.pixelPosInGroup.x + ix]
                        += kX * dequantY;
                    dequantHFCoeff[2][varblock.pixelPosInGroup.y + iy][varblock.pixelPosInGroup.x + ix]
                        += kB * dequantY;
                }
            }
        }
    }

    public void finalizeLLF() {
        final float[][][] scratchBlock = new float[2][256][256];
        final IntPoint[] shift = frame.getFrameHeader().jpegUpsampling;
        // put the LF coefficients into the HF coefficent array
        for (int i = 0; i < varblocks.length; i++) {
            final Varblock varblock = varblocks[i];
            if (varblock == null)
                continue;
            final IntPoint size = varblock.sizeInBlocks();
            final TransformType tt = varblock.transformType();
            for (int c = 0; c < 3; c++) {
                if (!varblock.isCorner(shift[c]))
                    continue;
                final float[][] dqlf = lfg.lfCoeff.dequantLFCoeff[c];
                final float[][] dq = dequantHFCoeff[c];
                final IntPoint ppg = varblock.pixelPosInGroup.shiftRight(shift[c]);
                MathHelper.forwardDCT2D(dqlf, dq,
                    varblock.blockPosInLFGroup.shiftRight(shift[c]),
                    ppg, size, scratchBlock[0], scratchBlock[1]);
                for (int y = 0; y < size.y; y++) {
                    final float[] dqy = dq[y + ppg.y];
                    final float[] llfy = tt.llfScale[y];
                    for (int x = 0; x < size.x; x++)
                        dqy[x + ppg.x] *= llfy[x];
                }
            }
        }
    }

    private int getBlockContext(final int c, final int orderID, final int hfMult, final int lfIndex) {
        int idx = (c < 2 ? 1 - c : c) * 13 + orderID;
        idx *= hfctx.qfThresholds.length + 1;
        for (final int t : hfctx.qfThresholds) {
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

    private int getPredictedNonZeroes(int c, IntPoint pos) {
        if (pos.x == 0 && pos.y == 0)
            return 32;
        if (pos.x == 0)
            return nonZeroes[c][pos.y - 1][0];
        if (pos.y == 0)
            return nonZeroes[c][0][pos.x - 1];
        return (nonZeroes[c][pos.y - 1][pos.x] + nonZeroes[c][pos.y][pos.x - 1] + 1) >> 1;
    }

    private void dequantizeHFCoefficients() {
        final OpsinInverseMatrix matrix = frame.globalMetadata.getOpsinInverseMatrix();
        final float globalScale = (float)(1 << 16) / frame.getLFGlobal().quantizer.globalScale;
        final float[] scaleFactor = new float[]{
            globalScale * (float)Math.pow(0.8D, frame.getFrameHeader().xqmScale - 2D),
            globalScale,
            globalScale * (float)Math.pow(0.8D, frame.getFrameHeader().bqmScale - 2D),
        };
        final float[][][][] weights = frame.getHFGlobal().weights;
        final IntPoint[] shift = frame.getFrameHeader().jpegUpsampling;
        for (int i = 0; i < varblocks.length; i++) {
            final Varblock varblock = varblocks[i];
            if (varblock == null)
                continue;
            final TransformType tt = varblock.transformType();
            final boolean flip = tt.flip();
            final float[][][] w2 = weights[tt.parameterIndex];
            for (int c = 0; c < 3; c++) {
                if (!varblock.isCorner(shift[c]))
                    continue;
                final float[][] w3 = w2[c];
                final float qbc = matrix.quantBias[c];
                final float sfc = scaleFactor[c] / varblock.hfMult();
                final IntPoint ppg = varblock.pixelPosInGroup.shiftRight(shift[c]);
                for (int y = 0; y < tt.blockHeight; y++) {
                    for (int x = 0; x < tt.blockWidth; x++) {
                        final int coeff = quantizedCoeffs[c][ppg.y + y][ppg.x + x];
                        final float quant;
                        if (coeff == 0) {
                            quant = 0f;
                        } else if (coeff == 1) {
                            quant = qbc;
                        } else if (coeff == -1) {
                            quant = -qbc;
                        } else {
                            quant = coeff - matrix.quantBiasNumerator / coeff;
                        }
                        final int wx = flip ? y : x;
                        final int wy = y ^ x ^ wx;
                        dequantHFCoeff[c][ppg.y + y][ppg.x + x] = quant * sfc * w3[wy][wx];
                    }
                }
            }
        }
    }
}
