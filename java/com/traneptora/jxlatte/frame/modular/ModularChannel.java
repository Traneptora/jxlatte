package com.traneptora.jxlatte.frame.modular;

import java.io.IOException;
import java.util.Arrays;

import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.FlowHelper;
import com.traneptora.jxlatte.util.IntPoint;
import com.traneptora.jxlatte.util.MathHelper;

public class ModularChannel extends ModularChannelInfo {

    private static final int[] oneL24OverKP1 = new int[64];

    static {
        for (int i = 0; i < oneL24OverKP1.length; i++) {
            oneL24OverKP1[i] = (1 << 24) / (i + 1);
        }
    }

    private static int tendency(int a, int b, int c) {
        if (a >= b && b >= c) {
            int x = (4 * a - 3 * c - b + 6) / 12;
            int d = 2 * (a - b);
            int e = 2 * (b - c);
            if ((x - (x & 1)) > d)
                x = d + 1;
            if ((x + (x & 1)) > e)
                x = e;
            return x;
        }

        if (a <= b && b <= c) {
            int x = (4 * a - 3 * c - b - 6) / 12;
            int d = 2 * (a - b);
            int e = 2 * (b - c);
            if ((x + (x & 1)) < d)
                x = d - 1;
            if ((x - (x & 1)) < e)
                x = e;
            return x;
        }

        return 0;
    }

    public int[][] buffer;
    protected int[][][] error;
    protected int[][] pred;
    private int[] subpred;
    private int[] weight;
    private boolean decoded;

    public ModularChannel(ModularChannelInfo info) {
        super(info);
        if (width == 0 || height == 0) {
            buffer = new int[0][];
        } else {
            buffer = new int[height][width];
        }
        decoded = false;
    }

    public ModularChannel(int width, int height, int hshift, int vshift) {
        super(width, height, hshift, vshift);
    }

    public ModularChannel(ModularChannel copy) {
        this(ModularChannelInfo.class.cast(copy));
        if (copy.buffer != null) {
            this.buffer = new int[height][];
            for (int y = 0; y < height; y++) {
                this.buffer[y] = Arrays.copyOf(copy.buffer[y], copy.buffer[y].length);
            }
        }
        this.decoded = copy.decoded;
    }

    private int west(int x, int y) {
        return x > 0 ? buffer[y][x - 1] : y > 0 ? buffer[y - 1][x] : 0;
    }

    private int north(int x, int y) {
        return y > 0 ? buffer[y - 1][x] : x > 0 ? buffer[y][x - 1] : 0;
    }

    private int northWest(int x, int y) {
        return x > 0 && y > 0 ? buffer[y - 1][x - 1] : west(x, y);
    }

    private int northEast(int x, int y) {
        return x + 1 < width && y > 0 ? buffer[y - 1][x + 1] : north(x, y);
    }

    private int northNorth(int x, int y) {
        return y > 1 ? buffer[y - 2][x] : north(x, y);
    }

    private int northEastEast(int x, int y) {
        return x + 2 < width && y > 0 ? buffer[y - 1][x + 2] : northEast(x, y);
    }

    private int westWest(int x, int y) {
        return x > 1 ? buffer[y][x - 2] : west(x, y);
    }

    private int errorWest(int x, int y, int e) {
        return x > 0 ? error[e][y][x - 1]: 0;
    }

    private int errorNorth(int x, int y, int e) {
        return y > 0 ? error[e][y - 1][x]: 0;
    }

    private int errorWestWest(int x, int y, int e) {
        return x > 1 ? error[e][y][x - 2] : 0;
    }

    private int errorNorthWest(int x, int y, int e) {
        return x > 0 && y > 0 ? error[e][y - 1][x - 1] : errorNorth(x, y, e);
    }

    private int errorNorthEast(int x, int y, int e) {
        return x + 1 < width && y > 0 ? error[e][y - 1][x + 1] : errorNorth(x, y, e);
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
                return MathHelper.clamp(v, n, w);
            case 6:
                return (pred[y][x] + 3) >> 3;
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
                throw new IllegalStateException();
        }
    }

    private int prePredictWP(WPParams wpParams, int x, int y) {
        int n3 = north(x, y) << 3;
        int nw3 = northWest(x, y) << 3;
        int ne3 = northEast(x, y) << 3;
        int w3 = west(x, y) << 3;
        int nn3 = northNorth(x, y) << 3;
        int tN = errorNorth(x, y, 4);
        int tW = errorWest(x, y, 4);
        int tNE = errorNorthEast(x, y, 4);
        int tNW = errorNorthWest(x, y, 4);
        subpred[0] = w3 + ne3 - n3;
        subpred[1] = n3 - (((tW + tN + tNE) * wpParams.param1) >> 5);
        subpred[2] = w3 - (((tW + tN + tNW) * wpParams.param2) >> 5);
        subpred[3] = n3 - ((tNW * wpParams.param3a
            + tN * wpParams.param3b
            + tNE * wpParams.param3c
            + (nn3 - n3) * wpParams.param3d
            + (nw3 - w3) * wpParams.param3e) >> 5);
        int wSum = 0;
        for (int e = 0; e < 4; e++) {
            int eSum = errorNorth(x, y, e) + errorWest(x, y, e) + errorNorthWest(x, y, e)
                + errorWestWest(x, y, e) + errorNorthEast(x, y, e);
            if (x + 1 == width)
                eSum += errorWest(x, y, e);
            int shift = MathHelper.floorLog1p(eSum) - 5;
            if (shift < 0)
                shift = 0;
            weight[e] = 4 + ((wpParams.weight[e] * oneL24OverKP1[eSum >> shift]) >> shift);
            wSum += weight[e];
        }
        int logWeight = MathHelper.floorLog1p(wSum - 1) - 4;
        wSum = 0;
        for (int e = 0; e < 4; e++) {
            weight[e] >>= logWeight;
            wSum += weight[e];
        }
        long s = (wSum >> 1) - 1L;
        for (int e = 0; e < 4; e++)
            s += subpred[e] * weight[e];
        pred[y][x] = (int)((s * oneL24OverKP1[wSum - 1]) >> 24);
        if (((tN ^ tW) | (tN ^ tNW)) <= 0)
            pred[y][x] = MathHelper.clamp(pred[y][x], w3, n3, ne3);
        int maxError = tW;
        if (Math.abs(tN) > Math.abs(maxError))
            maxError = tN;
        if (Math.abs(tNW) > Math.abs(maxError))
            maxError = tNW;
        if (Math.abs(tNE) > Math.abs(maxError))
            maxError = tNE;
        return maxError;
    }

    public void decode(Bitreader reader, EntropyStream stream, WPParams wpParams, MATree tree,
            ModularStream parent, int channelIndex, int streamIndex, int distMultiplier) throws IOException {
        if (decoded)
            return;
        decoded = true;
        tree = tree.compactify(channelIndex, streamIndex);
        boolean useWP = forceWP || tree.usesWeightedPredictor();
        if (useWP) {
            error = new int[5][height][width];
            pred = new int[height][width];
            subpred = new int[4];
            weight = new int[4];
        }
        for (final int y : FlowHelper.range(height)) {
            MATree refinedTree = tree.compactify(channelIndex, streamIndex, y);
            for (final int x : FlowHelper.range(width)) {
                final int maxError;
                if (useWP)
                    maxError = prePredictWP(wpParams, x, y);
                else
                    maxError = 0;
                MATree leafNode = refinedTree.walk(k -> {
                    switch (k) {
                        case 0:
                            return channelIndex;
                        case 1:
                            return streamIndex;
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
                                ? west(x, y) - (west(x - 1, y) + north(x - 1, y) - northWest(x - 1, y))
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
                            if (k - 16 >= 4 * channelIndex)
                                return 0;
                            int k2 = 16;
                            for (int j = channelIndex - 1; j >= 0; j--) {
                                ModularChannel channel = parent.getChannel(j);
                                if (channel.width != width || channel.height != height
                                        || channel.hshift != hshift || channel.vshift != vshift)
                                    continue;
                                if (k2 + 4 <= k) {
                                    k2 += 4;
                                    continue;
                                }
                                int rC = channel.buffer[y][x];
                                if (k2++ == k)
                                    return Math.abs(rC);
                                if (k2++ == k)
                                    return rC;
                                int rW = x > 0 ? channel.buffer[y][x - 1] : 0;
                                int rN = y > 0 ? channel.buffer[y - 1][x] : rW;
                                int rNW = x > 0 && y > 0 ? channel.buffer[y - 1][x - 1] : rW;
                                int rG = rC - MathHelper.clamp(rW + rN - rNW, rN, rW);
                                if (k2++ == k)
                                    return Math.abs(rG);
                                if (k2++ == k)
                                    return rG;
                            }
                            return 0;
                    }
                });
                int diff = stream.readSymbol(reader, leafNode.getContext(), distMultiplier);
                diff = MathHelper.unpackSigned(diff) * leafNode.getMultiplier() + leafNode.getOffset();
                int trueValue = diff + prediction(x, y, leafNode.getPredictor());
                buffer[y][x] = trueValue;
                if (useWP) {
                    for (int e = 0; e < 4; e++)
                        error[e][y][x] = (Math.abs(subpred[e] - (trueValue << 3)) + 3) >> 3;
                    error[4][y][x] = pred[y][x] - (trueValue << 3);
                }
            }
        }
    }

    public boolean isDecoded() {
        return decoded;
    }

    public static ModularChannel inverseHorizontalSqueeze(FlowHelper flowHelper,
            ModularChannelInfo info, ModularChannel orig, ModularChannel res) {
        if (info.width != orig.width + res.width
                || (orig.width != res.width && orig.width != 1 + res.width)
                || info.height != orig.height || res.height != orig.height)
            throw new IllegalArgumentException("Corrupted squeeze transform");
        ModularChannel channel = new ModularChannel(info);
        for (IntPoint p : FlowHelper.range2D(res.width, channel.height)) {
            int x = p.x, y = p.y;
            int avg = orig.buffer[y][x];
            int residu = res.buffer[y][x];
            int nextAvg = x + 1 < orig.width ? orig.buffer[y][x + 1] : avg;
            int left = x > 0 ? channel.buffer[y][2*x - 1] : avg;
            int diff = residu + tendency(left, avg, nextAvg);
            int first = avg + diff / 2;
            channel.buffer[y][2*x] = first;
            channel.buffer[y][2*x + 1] = first - diff;
        }
        if (orig.width > res.width) {
            for (int y = 0; y < channel.height; y++)
                channel.buffer[y][2*res.width] = orig.buffer[y][res.width];
        }

        return channel;
    }

    public static ModularChannel inverseVerticalSqueeze(FlowHelper flowHelper,
            ModularChannelInfo info, ModularChannel orig, ModularChannel res) {
        if (info.height != orig.height + res.height
                || (orig.height != res.height && orig.height != 1 + res.height)
                || info.width != orig.width || res.width != orig.width)
            throw new IllegalStateException("Corrupted squeeze transform");

        ModularChannel channel = new ModularChannel(info);
        for (IntPoint p : FlowHelper.range2D(channel.width, res.height)) {
            int x = p.x, y = p.y;
            int avg = orig.buffer[y][x];
            int residu = res.buffer[y][x];
            int nextAvg = y + 1 < orig.height ? orig.buffer[y + 1][x] : avg;
            int top = y > 0 ? channel.buffer[2*y - 1][x] : avg;
            int diff = residu + tendency(top, avg, nextAvg);
            int first = avg + diff / 2;
            channel.buffer[2*y][x] = first;
            channel.buffer[2*y + 1][x] = first - diff;
        }

        if (orig.height > res.height)
            System.arraycopy(orig.buffer[res.height], 0, channel.buffer[2*res.height], 0, channel.width);

        return channel;
    }
}
