package com.thebombzen.jxlatte.frame.vardct;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.color.OpsinInverseMatrix;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.group.LFGroup;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.FlowHelper;
import com.thebombzen.jxlatte.util.IntPoint;
import com.thebombzen.jxlatte.util.MathHelper;

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
    private HFBlockContext hfctx;
    public final LFGroup lfg;
    public final int groupID;
    private int[][][] nonZeroes;
    public final float[][][][] dequantHFCoeff;
    public final EntropyStream stream;
    public final Frame frame;
    public final Varblock[] varblocks;

    public HFCoefficients(Bitreader reader, Frame frame, int pass, int group) throws IOException {
        hfPreset = reader.readBits(MathHelper.ceilLog1p(frame.getHFGlobal().numHfPresets - 1));
        this.groupID = group;
        this.frame = frame;
        this.hfctx = frame.getLFGlobal().hfBlockCtx;
        this.lfg = frame.getLFGroupForGroup(group);
        int offset = 495 * hfctx.numClusters * hfPreset;
        HFPass hfPass = frame.getHFPass(pass);
        IntPoint groupSize = frame.groupSize(group);
        IntPoint groupBlockSize = groupSize.shiftRight(3);
        nonZeroes = new int[3][groupBlockSize.y][groupBlockSize.x];
        List<int[][][]> coeffs = new ArrayList<>();
        IntPoint[] shift = frame.getFrameHeader().jpegUpsampling;
        stream = new EntropyStream(hfPass.contextStream);
        List<Varblock> varBlockList = new ArrayList<>();
        for (int i = 0; i < lfg.hfMetadata.blockList.length; i++) {
            Varblock varblock = lfg.hfMetadata.blockList[i].get(lfg.hfMetadata.blockMap);
            if (!varblock.groupPosInLFGroup.equals(frame.groupPosInLFGroup(lfg.lfGroupID, groupID)))
                continue; // block is not in this group
            varBlockList.add(varblock);
            TransformType tt = varblock.transformType();
            int[][][] co = new int[3][tt.blockHeight][tt.blockWidth];
            coeffs.add(co);
            for (int c : Frame.cMap) {
                if (!varblock.isCorner(shift[c]))
                    continue; // subsampled block
                IntPoint sizeInBlocks = varblock.sizeInBlocks();
                int numBlocks = sizeInBlocks.x * sizeInBlocks.y;
                int predicted = getPredictedNonZeroes(c, varblock.blockPosInGroup.shiftRight(shift[c]));
                int lfIndex = varblock.blockPosInLFGroup.get(lfg.lfCoeff.lfIndex);
                int blockCtx = getBlockContext(c, varblock, lfIndex);
                int nonZeroCtx = offset + getNonZeroContext(predicted, blockCtx);
                int nonZero = stream.readSymbol(reader, nonZeroCtx);
                for (IntPoint p : FlowHelper.range2D(sizeInBlocks)) {
                    varblock.blockPosInGroup.shiftRight(shift[c]).plus(p).set(nonZeroes[c],
                        (nonZero + numBlocks - 1) / numBlocks);
                }
                // SPEC: spec doesn't say you abort here if nonZero == 0
                if (nonZero <= 0)
                    continue;
                int size = hfPass.order[tt.orderID][c].length;
                int[] ucoeff = new int[size - numBlocks];
                int histCtx = offset + 458 * blockCtx + 37 * hfctx.numClusters;
                for (int k = 0; k < ucoeff.length; k++) {
                    // SPEC: spec has this condition flipped
                    int prev = k == 0 ? (nonZero > size/16 ? 0 : 1) : (ucoeff[k - 1] != 0 ? 1 : 0);
                    int ctx = histCtx + getCoefficientContext(k + numBlocks, nonZero, numBlocks, prev);
                    ucoeff[k] = stream.readSymbol(reader, ctx);
                    IntPoint orderPos = hfPass.order[tt.orderID][c][k + numBlocks];
                    IntPoint pos = varblock.flip() || tt.isVertical() ? orderPos.transpose() : orderPos;
                    co[c][pos.y][pos.x] = MathHelper.unpackSigned(ucoeff[k]);
                    if (ucoeff[k] != 0)
                        nonZero--;
                    if (nonZero <= 0)
                        break;
                }
                // SPEC: spec doesn't mention that nonZero > 0 is illegal
                if (nonZero != 0)
                    throw new InvalidBitstreamException("Illegal final nonzero count: " + nonZero);
            }
        }
        if (!stream.validateFinalState())
            throw new InvalidBitstreamException("Illegal final state in PassGroup: " + pass + ", " + group);
        this.varblocks = varBlockList.stream().toArray(Varblock[]::new);

        this.dequantHFCoeff = dequantizeHFCoefficients(coeffs.stream().toArray(int[][][][]::new));

        // chroma from luma
        // shifts are nonnegative so the sum equals zero iff they all equal zero
        if (shift[0].plus(shift[1]).plus(shift[2]).equals(IntPoint.ZERO)) {
            LFChannelCorrelation lfc = frame.getLFGlobal().lfChanCorr;
            int[][] xFactorHF = lfg.hfMetadata.hfStreamBuffer[0];
            int[][] bFactorHF = lfg.hfMetadata.hfStreamBuffer[1];
            for (int i = 0; i < varblocks.length; i++) {
                Varblock varblock = varblocks[i];
                for (IntPoint pixelPosInVarblock : FlowHelper.range2D(varblock.sizeInPixels())) {
                    IntPoint factorPos = varblock.pixelPosInLFGroup.plus(pixelPosInVarblock).divide(64);
                    float kX = lfc.baseCorrelationX + factorPos.get(xFactorHF) / (float)lfc.colorFactor;
                    float kB = lfc.baseCorrelationB + factorPos.get(bFactorHF) / (float)lfc.colorFactor;
                    float dequantY = pixelPosInVarblock.get(dequantHFCoeff[i][1]);
                    float dequantX = pixelPosInVarblock.get(dequantHFCoeff[i][0]);
                    float dequantB = pixelPosInVarblock.get(dequantHFCoeff[i][2]);
                    pixelPosInVarblock.set(dequantHFCoeff[i][0], dequantX + kX * dequantY);
                    pixelPosInVarblock.set(dequantHFCoeff[i][2], dequantB + kB * dequantY);
                }
            }
        }
        float[][][] scratchBlock = new float[2][256][256];
        // put the LF coefficients into the HF coefficent array
        for (int i = 0; i < varblocks.length; i++) {
            Varblock varblock = varblocks[i];
            IntPoint size = varblock.sizeInBlocks();
            float[][] lfCoeffs = new float[size.y][size.x];
            for (int c = 0; c < 3; c++) {
                if (!varblock.isCorner(shift[c]))
                    continue;
                MathHelper.forwardDCT2D(lfg.lfCoeff.dequantLFCoeff[c], lfCoeffs,
                    varblock.blockPosInLFGroup.shiftRight(shift[c]),
                    IntPoint.ZERO, size, scratchBlock[0], scratchBlock[1]);
                for (IntPoint p : FlowHelper.range2D(size)) {
                    float llf = p.get(lfCoeffs) * p.get(varblock.transformType().llfScale);
                    p.set(dequantHFCoeff[i][c], llf);
                }
            }
        }
    }

    private int getBlockContext(int c, Varblock varblock, int lfIndex) {
        int s = varblock.transformType().orderID;
        int idx = (c < 2 ? 1 - c : c) * 13 + s;
        idx *= hfctx.qfThresholds.length + 1;
        int qf = varblock.hfMult();
        for (int t : hfctx.qfThresholds) {
            if (qf > t)
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

    private float[][][][] dequantizeHFCoefficients(int[][][][] coeffs) {
        OpsinInverseMatrix matrix = frame.globalMetadata.getOpsinInverseMatrix();
        float[][][][] dequant = new float[varblocks.length][3][][];
        float globalScale = (float)(1 << 16) / frame.getLFGlobal().quantizer.globalScale;
        float[] scaleFactor = new float[]{
            globalScale * (float)Math.pow(0.8D, frame.getFrameHeader().xqmScale - 2D),
            globalScale,
            globalScale * (float)Math.pow(0.8D, frame.getFrameHeader().bqmScale - 2D),
        };
        float[][][][] weights = frame.getHFGlobal().weights;
        IntPoint[] shift = frame.getFrameHeader().jpegUpsampling;
        for (int i = 0; i < varblocks.length; i++) {
            Varblock varblock = varblocks[i];
            for (int c : Frame.cMap) {
                if (!varblock.isCorner(shift[c]))
                    continue;
                int[][] co = coeffs[i][c];
                float[][] dq = new float[co.length][co[0].length];
                for (IntPoint pos : FlowHelper.range2D(varblock.sizeInPixels())) {
                    int coeff = pos.get(co);
                    float quant;
                    if (Math.abs(coeff) <= 1)
                        quant = coeff * matrix.quantBias[c];
                    else
                        quant = coeff - matrix.quantBiasNumerator / coeff;
                    // SPEC: wrong scale factor here and hfmul multiplier
                    quant *= scaleFactor[c] / varblock.hfMult();
                    IntPoint weightPos = varblock.transformType().isVertical()
                        ? pos.transpose() : pos;
                    quant *= weightPos.get(weights[varblock.transformType().parameterIndex][c]);
                    pos.set(dq, quant);
                }
                dequant[i][c] = dq;
            }
        }
        return dequant;
    }
}
