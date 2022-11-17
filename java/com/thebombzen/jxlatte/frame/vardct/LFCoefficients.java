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
    public final ModularStream lfQuant;
    public final double[][][] dequantLFCoeff;
    public final Frame frame;

    public LFCoefficients(Bitreader reader, LFGroup parent, Frame frame) throws IOException {
        this.frame = frame;
        if ((frame.getFrameHeader().flags & FrameFlags.USE_LF_FRAME) != 0) {
            System.err.println("TODO: Implement LF Frames");
            throw new UnsupportedOperationException("LF Frames currently not implemented");
        }
        IntPoint size = frame.getLFGroupSize(parent.lfGroupID);
        IntPoint lfSize = size.shiftLeft(-3);
        this.extraPrecision = reader.readBits(2);
        FrameHeader header = frame.getFrameHeader();
        ModularChannelInfo[] info = new ModularChannelInfo[3];
        double[][][] dequantLFCoeff = new double[3][][];
        boolean adaptiveSmoothing = (header.flags & FrameFlags.SKIP_ADAPTIVE_LF_SMOOTHING) == 0;
        for (int i = 0; i < 3; i++) {
            int hshift = header.jpegUpsampling[i].x;
            int vshift = header.jpegUpsampling[i].y;
            if (adaptiveSmoothing && (hshift > 0 || vshift > 0))
                throw new InvalidBitstreamException("Adaptive Smoothing is incompatible with subsampling");
            IntPoint channelSize = lfSize.shiftLeft(-hshift, -vshift);
            info[Frame.cMap[i]] = new ModularChannelInfo(channelSize.x, channelSize.y, hshift, vshift);
            dequantLFCoeff[i] = new double[channelSize.y][channelSize.x];
        }
        this.lfQuant = new ModularStream(reader, frame, 1 + parent.lfGroupID, info);
        lfQuant.decodeChannels(reader);
        double[] scaledDequant = frame.getLFGlobal().quantizer.scaledDequant;
        int[][][] quant = lfQuant.getDecodedBuffer();
        for (int i = 0; i < 3; i++) {
            for (int y = 0; y < quant[i].length; y++) {
                for (int x = 0; x < quant[i][y].length; x++) {
                    dequantLFCoeff[Frame.cMap[i]][y][x] = quant[i][y][x] * scaledDequant[Frame.cMap[i]] / (1 << extraPrecision);
                }
            }
        }
        if (adaptiveSmoothing) {
            this.dequantLFCoeff = adaptiveSmooth(dequantLFCoeff, scaledDequant, header.jpegUpsampling);
        } else {
            this.dequantLFCoeff = dequantLFCoeff;
        }
    }

    private double[][][] adaptiveSmooth(double[][][] coeff, double[] scaledDequant, IntPoint[] shift) {
        double[][][] weighted = new double[3][][];
        double[][] gap = new double[coeff[0].length][];
        double[][][] dequantLFCoeff = new double[3][][];
        for (int i = 0; i < 3; i++) {
            weighted[i] = new double[coeff[i].length][];
            for (int y = 1; y < coeff[i].length - 1; y++) {
                if (gap[y] == null) {
                    gap[y] = new double[coeff[i][y].length];
                    Arrays.fill(gap[y], 0.5D);
                }
                // we never use weighted[i][0] so it can stay null
                weighted[i][y] = new double[coeff[i][y].length];
                for (int x = 1; x < coeff[i][y].length - 1; x++) {
                    double sample = coeff[i][y][x];
                    double adjacent = coeff[i][y][x - 1] + coeff[i][y][x + 1]
                        + coeff[i][y - 1][x] + coeff[i][y + 1][x];
                    double diag = coeff[i][y - 1][x - 1] + coeff[i][y - 1][x + 1]
                        + coeff[i][y + 1][x - 1] + coeff[i][y + 1][x + 1];
                    weighted[i][y][x] = 0.05226273532324128D * sample + 0.20345139757231578D * adjacent
                        + 0.0334829185968739 * diag;
                    double g = Math.abs(sample - weighted[i][y][x]) / coeff[Frame.cMap[i]][y][x];
                    if (g > gap[y][x])
                        gap[y][x] = g;
                }
            }
        }
        for (int y = 0; y < gap.length; y++) {
            if (gap[y] == null)
                continue;
            for (int x = 0; x < gap[y].length; x++) {
                gap[y][x] = Math.max(0D, 3D - 4D * gap[y][x]);
            }
        }
        for (int i = 0; i < 3; i++) {
            dequantLFCoeff[i] = new double[coeff[i].length][];
            for (int y = 0; y < coeff[i].length; y++) {
                dequantLFCoeff[i][y] = new double[coeff[i][y].length];
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
            double kX = lfc.baseCorrelationX + (lfc.xFactorLF - 127D) / (double)lfc.colorFactor;
            double kB = lfc.baseCorrelationB + (lfc.bFactorLF - 127D) / (double)lfc.colorFactor;
            for (IntPoint p : FlowHelper.range2D(IntPoint.sizeOf(dequantLFCoeff[1]))) {
                dequantLFCoeff[0][p.y][p.x] += kX * dequantLFCoeff[1][p.y][p.x];
                dequantLFCoeff[2][p.y][p.x] += kB * dequantLFCoeff[1][p.y][p.x];
            }
        }
        return dequantLFCoeff;
    }
}
