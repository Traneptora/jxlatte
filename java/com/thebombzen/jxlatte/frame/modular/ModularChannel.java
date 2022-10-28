package com.thebombzen.jxlatte.frame.modular;

import java.io.IOException;
import java.util.Arrays;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.MathHelper;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.io.IOHelper;

public class ModularChannel {
    protected int width;
    protected int height;
    protected int hshift;
    protected int vshift;
    protected int[][] buffer;
    protected int[][] trueError;
    protected int[][][] error;
    protected int[][] pred;
    private ModularStream parent;

    public ModularChannel(ModularStream parent, int width, int height, int dimShift) throws IOException {
        this.parent = parent;
        this.width = width;
        this.height = height;
        hshift = dimShift;
        vshift = dimShift;
        if (hshift > 1)
            width = MathHelper.ceilDiv(width, 1 << hshift);
        if (vshift > 1)
            height = MathHelper.ceilDiv(height, 1 << vshift);
    }

    public ModularChannel(ModularChannel copy) {
        this.width = copy.width;
        this.height = copy.height;
        this.hshift = copy.hshift;
        this.vshift = copy.vshift;
        this.parent = copy.parent;
        if (copy.buffer != null) {
            this.buffer = new int[height][];
            for (int y = 0; y < height; y++) {
                this.buffer[y] = Arrays.copyOf(copy.buffer[y], width);
            }
        }
    }

    protected void set(int x, int y, int s) {
        buffer[y][x] = s;
    }

    protected int get(int x, int y) {
        return buffer[y][x];
    }

    private int west(int x, int y) {
        return x > 0 ? get(x - 1, y) : (y > 0 ? get(x, y - 1) : 0);
    }

    private int north(int x, int y) {
        return y > 0 ? get(x, y - 1) : west(x, y);
    }

    private int northWest(int x, int y) {
        return x > 0 && y > 0 ? get(x - 1, y - 1) : west(x, y);
    }

    private int northEast(int x, int y) {
        return x + 1 < width && y > 0 ? get(x + 1, y - 1) : north(x, y);
    }

    private int northNorth(int x, int y) {
        return y > 1 ? get(x, y - 2) : north(x, y);
    }

    private int northEastEast(int x, int y) {
        return x + 2 < width && y > 0 ? get(x + 2, y - 1) : northEast(x, y);
    }

    private int westWest(int x, int y) {
        return x > 1 ? get(x - 2, y) : west(x, y);
    }

    private int teWest(int x, int y, int e) {
        return x > 0 ? (e < 0 ? trueError[x - 1][y] : error[x - 1][y][e]): 0;
    }

    private int teNorth(int x, int y, int e) {
        return y > 0 ? (e < 0 ? trueError[x][y - 1] : error[x][y - 1][e]): 0;
    }

    private int teWestWest(int x, int y, int e) {
        return x > 1 ? (e < 0 ? trueError[x - 2][y] : error[x - 2][y][e]) : 0;
    }

    private int teNorthWest(int x, int y, int e) {
        return x > 0 && y > 0 ? (e < 0 ? trueError[x - 1][y - 1] : error[x - 1][y - 1][e]) : teNorth(x, y, e);
    }

    private int teNorthEast(int x, int y, int e) {
        return x + 1 < width && y > 0 ? (e < 0 ? trueError[x + 1][y - 1] : error[x + 1][y - 1][e]) : teNorth(x, y, e);
    }

    protected int prediction(int x, int y, int k) {
        int n, v, nw, w;
        switch (k) {
            case 0:
                return 0;
            case 1:
                return west(x, y);
            case 2:
                return north(x, y);
            case 3:
                return (west(x, y) + north(x, y)) / 2;
            case 4:
                w = west(x, y);
                n = north(x, y);
                nw = northWest(x, y);
                return Math.abs(n - nw) < Math.abs(w - nw) ? w : n;
            case 5:
                w = west(x, y);
                n = north(x, y);
                v = w + n - northWest(x, y);
                return MathHelper.clamp(v, Math.min(n, w), Math.max(n, w));
            case 6:
                return (pred[x][y] + 3) >> 3;
            case 7:
                return northEast(x, y);
            case 8:
                return northWest(x, y);
            case 9:
                return westWest(x, y);
            case 10:
                return (west(x, y) + northWest(x, y)) / 2;
            case 11:
                return (north(x, y) + northWest(x, y)) / 2;
            case 12:
                return (north(x, y) + northEast(x, y)) / 2;
            case 13:
                return (6*north(x, y) - 2*northNorth(x, y) + 7*west(x, y) + westWest(x, y) + northEastEast(x, y) + 3*northEast(x, y)+8) / 16;
            default:
                return 0;
        }
    }

    public void decode(Bitreader reader, MATree tree, int channelIndex, int distMultiplier) throws IOException {
        if (width == 0 || height == 0)
            return;
        if (buffer != null)
            return;
        buffer = new int[height][width];
        trueError = new int[width][height];
        error = new int[width][height][4];
        pred = new int[width][height];
        for (int y0 = 0; y0 < height; y0++) {
            for (int x0 = 0; x0 < width; x0++) {
                final int y = y0;
                final int x = x0;
                int n3 = 8 * north(x, y);
                int nw3 = 8 * northWest(x, y);
                int ne3 = 8 * northEast(x, y);
                int w3 = 8 * west(x, y);
                int nn3 = 8 * northNorth(x, y);
                int tN = teNorth(x, y, -1);
                int tW = teWest(x, y, -1);
                int tNE = teNorthEast(x, y, -1);
                int tNW = teNorthWest(x, y, -1);
                int[] subpred = new int[4];
                subpred[0] = w3 + ne3 - n3;
                subpred[1] = n3 - (((tW + tN + tNE) * parent.wpParams.wp_p1) >> 5);
                subpred[2] = w3 - (((tW + tN + tNW) * parent.wpParams.wp_p2) >> 5);
                subpred[3] = n3 - ((tNW * parent.wpParams.wp_p3a
                    + tN * parent.wpParams.wp_p3b
                    + tNE * parent.wpParams.wp_p3c
                    + (nn3 - n3) * parent.wpParams.wp_p3d
                    + (nw3 - w3) * parent.wpParams.wp_p3e) >> 5);
                long[] weight = new long[4];
                long wSum = 0;
                for (int e = 0; e < 4; e++) {
                    int eSum = teNorth(x, y, e) + teWest(x, y, e) + teNorthWest(x, y, e)
                        + teWestWest(x, y, e) + teNorthEast(x, y, e);
                    if (x == width - 1)
                        eSum += teWest(x, y, e);
                    int shift = MathHelper.ceilLog1p(eSum) - 5;
                    if ((eSum & (eSum + 1)) != 0)
                        shift -= 1;
                    if (shift < 0)
                        shift = 0;
                    weight[e] = 4L + (((long)parent.wpParams.wp_w[e] * ((1L << 24) / ((eSum >> shift) + 1))) >> shift);
                    
                    wSum += weight[e];
                }
                int logWeight = MathHelper.ceilLog1p(wSum);
                
                wSum = 0;
                for (int e = 0; e < 4; e++) {
                    weight[e] >>= logWeight - 5;
                    wSum += weight[e];
                }
                long s = (wSum >> 1) - 1L;
                for (int e = 0; e < 4; e++) {
                    s += subpred[e] * weight[e];
                }

                pred[x][y] = (int)(((s * ((1L << 24) / wSum)) >> 24) & 0xFF_FF_FF_FFL);
                if (((tN ^ tW) | (tN ^ tNW)) <= 0)
                    pred[x][y] = MathHelper.clamp(pred[x][y], MathHelper.min3(w3, n3, ne3), MathHelper.max3(w3, n3, ne3));
                int maxError0 = tW;
                if (Math.abs(tN) > Math.abs(maxError0))
                    maxError0 = tN;
                if (Math.abs(tNW) > Math.abs(maxError0))
                    maxError0 = tNW;
                if (Math.abs(tNE) > Math.abs(maxError0))
                    maxError0 = tNE;
                final int maxError = maxError0;

                MALeafNode node = tree.walk(k -> {
                    switch (k) {
                        case 0:
                            return channelIndex;
                        case 1:
                            return parent.streamIndex;
                        case 2:
                            return y;
                        case 3:
                            return x;
                        case 4:
                            return Math.abs(north(x, y));
                        case 5:
                            return Math.abs(west(x, y));
                        case 6:
                            return north(x, y);
                        case 7:
                            return west(x, y);
                        case 8:
                            return x > 0
                                ? west(x, y) - west(x - 1, y) - north(x - 1, y) + northWest(x - 1, y)
                                : west(x, y);
                        case 9:
                            return west(x, y) + north(x, y) - northWest(x, y);
                        case 10:
                            return west(x, y) - northWest(x, y);
                        case 11:
                            return northWest(x, y) - north(x, y);
                        case 12:
                            return north(x, y) - northEast(x, y);
                        case 13:
                            return north(x, y) - northNorth(x, y);
                        case 14:
                            return west(x, y) - westWest(x, y);
                        case 15:
                            return maxError;
                        default:
                            IOHelper.sneakyThrow(new InvalidBitstreamException("Invalid property index"));
                            return 0;
                    }
                });

                int diff = tree.stream.readSymbol(reader, node.context, distMultiplier);
                diff = MathHelper.unpackSigned(diff) * node.multiplier + node.offset;
                int trueValue = diff + prediction(x, y, node.predictor);
                set(x, y, trueValue);
                trueError[x][y] = pred[x][y] - (trueValue << 3);
                for (int e = 0; e < 4; e++)
                    error[x][y][e] = (Math.abs(subpred[e] - (trueValue * 8)) + 3) >> 3;
            }
        }
    }

    public void clamp() {
        int maxValue = ~(~0 << parent.frame.globalMetadata.getBitDepthHeader().bitsPerSample);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = get(x, y);
                value = MathHelper.clamp(value, 0, maxValue);
                set(x, y, value);
            }
        }
    }
}
