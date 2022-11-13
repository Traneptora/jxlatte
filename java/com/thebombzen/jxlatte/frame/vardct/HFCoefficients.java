package com.thebombzen.jxlatte.frame.vardct;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.color.OpsinInverseMatrix;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.group.LFGroup;
import com.thebombzen.jxlatte.io.Bitreader;
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
    private LFGroup lfg;
    public final int groupID;
    private int[][][] nonZeroes;
    public final double[][][] dequantHFCoeff;
    public final EntropyStream stream;
    public final Frame frame;

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
        int groupBlockDim = frame.getFrameHeader().groupDim >> 3;
        nonZeroes = new int[3][groupBlockSize.y][groupBlockSize.x];
        int[][][] coeffs = new int[3][groupSize.y][groupSize.x];
        stream = new EntropyStream(hfPass.contextStream);
        for (int i = 0; i < lfg.hfMetadata.blockList.length; i++) {
            IntPoint blockPosBase = lfg.hfMetadata.blockList[i];
            IntPoint blockPosBaseGroup = blockPosBase.minus(frame.groupXY(group).times(groupBlockDim));
            IntPoint blockPosInGroup = blockPosBaseGroup.shiftLeft(3);
            if (blockPosBaseGroup.x >= groupBlockDim || blockPosBaseGroup.y >= groupBlockDim)
                continue; // this block is not in this group
            if (blockPosBaseGroup.x < 0 || blockPosBaseGroup.y < 0)
                continue; // this block is not in this group
            IntPoint[] shift = frame.getFrameHeader().jpegUpsampling;
            for (int c : Frame.cMap) {
                IntPoint blockPos = blockPosBase.shiftRight(shift[c]);
                if (!blockPos.shiftLeft(shift[c]).equals(blockPosBase))
                    continue; // subsampled block
                IntPoint blockPosGroup = blockPosBaseGroup.shiftRight(shift[c]);
                TransformType tt = lfg.hfMetadata.dctSelect[blockPos.y][blockPos.x];
                int w = tt.blockWidth;
                int h = tt.blockHeight;
                int numBlocks = (w / 8) * (h / 8);
                int predicted = getPredictedNonZeroes(c, blockPosGroup);
                int lfIndex = lfg.lfIndex[blockPosBase.y][blockPosBase.x];
                int blockCtx = getBlockContext(c, blockPos, lfIndex);
                int nonZeroCtx = offset + getNonZeroContext(predicted, blockCtx);
                int nonZero = stream.readSymbol(reader, nonZeroCtx);
                for (int y = 0; y < h / 8; y++) {
                    for (int x = 0; x < w / 8; x++) {
                        nonZeroes[c][y + blockPosGroup.y][x + blockPosGroup.x] = (nonZero + numBlocks - 1) / numBlocks;
                    }
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
                    // SPEC: spec doesn't mention the transpose requirement
                    IntPoint pos = blockPosInGroup.plus(tt.isHorizontal() ? orderPos.transpose() : orderPos);
                    coeffs[c][pos.y][pos.x] = MathHelper.unpackSigned(ucoeff[k]);
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
        this.dequantHFCoeff = dequantizeHFCoefficients(coeffs);
    }

    private int getBlockContext(int c, IntPoint blockPos, int lfIndex) {
        int s = lfg.hfMetadata.dctSelect[blockPos.y][blockPos.x].orderID;
        int idx = (c < 2 ? 1 - c : c) * 13 + s;
        idx *= hfctx.qfThresholds.length + 1;
        int qf = lfg.hfMetadata.hfMultiplier[blockPos.y][blockPos.x];
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

    private double[][][] dequantizeHFCoefficients(int[][][] coeffs) {
        OpsinInverseMatrix matrix = frame.globalMetadata.getOpsinInverseMatrix();
        double[][][] dequant = new double[3][][];
        IntPoint groupPosInLFGroup = frame.groupXY(groupID)
            .minus(frame.getLFGroupXY(lfg.lfGroupID).shiftLeft(3))
            .times(frame.getFrameHeader().groupDim);
        double[] scaleFactor = new double[]{
            1.0D,
            Math.pow(0.8D, frame.getFrameHeader().xqmScale - 2D),
            Math.pow(0.8D, frame.getFrameHeader().bqmScale - 2D),
        };
        double[][][][] weights = frame.getHFGlobal().weights;
        for (int i = 0; i < 3; i++) {
            dequant[i] = new double[coeffs[i].length][];
            for (int y = 0; y < coeffs[i].length; y++) {
                dequant[i][y] = new double[coeffs[i][y].length];
                for (int x = 0; x < coeffs[i][y].length; x++) {
                    double quant = coeffs[i][y][x];
                    if (Math.abs(quant) <= 1) {
                        quant *= matrix.quantBias[Frame.cMap[i]];
                    } else {
                        quant -= matrix.quantBiasNumerator / quant;
                    }
                    IntPoint pos = groupPosInLFGroup.plus(new IntPoint(x, y)).shiftRight(3);
                    IntPoint varblockCorner = lfg.hfMetadata.blockMap.get(pos);
                    IntPoint posInVarblock = pos.minus(varblockCorner);
                    quant *= lfg.hfMetadata.hfMultiplier[varblockCorner.y][varblockCorner.x] * scaleFactor[i];
                    TransformType tt = lfg.hfMetadata.dctSelect[pos.y][pos.x];
                    IntPoint weightPos = tt.isHorizontal() ? posInVarblock.transpose() : posInVarblock;
                    quant *= weights[tt.parameterIndex][i][weightPos.y][weightPos.x];
                    dequant[i][y][x] = quant;
                }
            }
        }
        return dequant;
    }
}
