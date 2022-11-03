package com.thebombzen.jxlatte.frame.modular;

import java.io.IOException;
import java.util.Arrays;

import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.MathHelper;

public class ModularChannel {

    private static int[] oneL24OverKP1 = new int[64];

    static {
        for (int i = 0; i < oneL24OverKP1.length; i++) {
            oneL24OverKP1[i] = (1 << 24) / (i + 1);
        }
    }

    ModularChannelInfo info;

    protected int[][] buffer;
    protected int[][] trueError;
    protected int[][][] error;
    protected int[][] pred;
    private int[] subpred;
    private int[] weight;
    private boolean decoded;

    private ModularChannel(ModularChannelInfo info, boolean copy) {
        this.info = copy ? new ModularChannelInfo(info) : info;
        if (info.width == 0 || info.height == 0) {
            buffer = new int[0][];
        } else {
            buffer = new int[info.height][info.width];
        }
        decoded = false;
    }

    public ModularChannel(int width, int height, int dimShift) {
        this(width, height, dimShift, dimShift);
    }

    public ModularChannel(int width, int height, int hshift, int vshift) {
        this(new ModularChannelInfo(width, height, hshift, vshift), false);
    }

    public ModularChannel(ModularChannelInfo info) {
        this(info, true);
    }

    public ModularChannel(ModularChannel copy) {
        this(copy.info, true);
        if (copy.buffer != null) {
            this.buffer = new int[info.height][];
            for (int y = 0; y < info.height; y++) {
                this.buffer[y] = Arrays.copyOf(copy.buffer[y], copy.buffer[y].length);
            }
        }
        this.decoded = copy.decoded;
    }

    public void set(int x, int y, int s) {
        if (y >= 0 && y < buffer.length && x >= 0 && x < buffer[y].length)
            buffer[y][x] = s;
    }

    public int get(int x, int y) {
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
        return x + 1 < info.width && y > 0 ? get(x + 1, y - 1) : north(x, y);
    }

    private int northNorth(int x, int y) {
        return y > 1 ? get(x, y - 2) : north(x, y);
    }

    private int northEastEast(int x, int y) {
        return x + 2 < info.width && y > 0 ? get(x + 2, y - 1) : northEast(x, y);
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
        return x + 1 < info.width && y > 0 ? (e < 0 ? trueError[x + 1][y - 1] : error[x + 1][y - 1][e]) : teNorth(x, y, e);
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
                throw new IllegalStateException();
        }
    }

    private int prePredictWP(WPHeader wpParams, int x, int y) {
        int n3 = north(x, y) << 3;
        int nw3 = northWest(x, y) << 3;
        int ne3 = northEast(x, y) << 3;
        int w3 = west(x, y) << 3;
        int nn3 = northNorth(x, y) << 3;
        int tN = teNorth(x, y, -1);
        int tW = teWest(x, y, -1);
        int tNE = teNorthEast(x, y, -1);
        int tNW = teNorthWest(x, y, -1);
        subpred[0] = w3 + ne3 - n3;
        subpred[1] = n3 - (((tW + tN + tNE) * wpParams.wp_p1) >> 5);
        subpred[2] = w3 - (((tW + tN + tNW) * wpParams.wp_p2) >> 5);
        subpred[3] = n3 - ((tNW * wpParams.wp_p3a
            + tN * wpParams.wp_p3b
            + tNE * wpParams.wp_p3c
            + (nn3 - n3) * wpParams.wp_p3d
            + (nw3 - w3) * wpParams.wp_p3e) >> 5);
        int wSum = 0;
        for (int e = 0; e < 4; e++) {
            int eSum = teNorth(x, y, e) + teWest(x, y, e) + teNorthWest(x, y, e)
                + teWestWest(x, y, e) + teNorthEast(x, y, e);
            if (x + 1 == info.width)
                eSum += teWest(x, y, e);
            int shift = MathHelper.floorLog1p(eSum) - 5;
            if (shift < 0)
                shift = 0;
            weight[e] = 4 + ((wpParams.wp_w[e] * oneL24OverKP1[eSum >> shift]) >> shift);
            wSum += weight[e];
        }
        int logWeight = MathHelper.floorLog1p(wSum - 1) - 4;
        wSum = 0;
        for (int e = 0; e < 4; e++) {
            weight[e] >>= logWeight;
            wSum += weight[e];
        }
        long s = (wSum >> 1) - 1L;
        for (int e = 0; e < 4; e++) {
            s += subpred[e] * weight[e];
        }
        pred[x][y] = (int)((s * oneL24OverKP1[wSum - 1]) >> 24);
        if (((tN ^ tW) | (tN ^ tNW)) <= 0)
            pred[x][y] = MathHelper.clamp(pred[x][y], MathHelper.min(w3, n3, ne3), MathHelper.max(w3, n3, ne3));
        int maxError = tW;
        if (Math.abs(tN) > Math.abs(maxError))
            maxError = tN;
        if (Math.abs(tNW) > Math.abs(maxError))
            maxError = tNW;
        if (Math.abs(tNE) > Math.abs(maxError))
            maxError = tNE;
        return maxError;
    }

    public void decode(Bitreader reader, EntropyStream stream, WPHeader wpParams, MATree tree,
            int channelIndex, int streamIndex, int distMultiplier) throws IOException {
        if (decoded)
            return;
        decoded = true;
        trueError = new int[info.width][info.height];
        error = new int[info.width][info.height][4];
        pred = new int[info.width][info.height];
        tree = tree.compactify(channelIndex, streamIndex);
        boolean useWP = info.forceWP || tree.usesWeightedPredictor();
        if (useWP) {
            subpred = new int[4];
            weight = new int[4];
        }
        for (int y0 = 0; y0 < info.height; y0++) {
            final int y = y0;
            MATree refinedTree = tree.compactify(channelIndex, streamIndex, y);
            for (int x0 = 0; x0 < info.width; x0++) {
                final int x = x0;
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
                            throw new UnsupportedOperationException("Properties > 15 not yet implmented");
                    }
                });
                int diff = stream.readSymbol(reader, leafNode.getContext(), distMultiplier);
                diff = MathHelper.unpackSigned(diff) * leafNode.getMultiplier() + leafNode.getOffset();
                int trueValue = diff + prediction(x, y, leafNode.getPredictor());
                set(x, y, trueValue);
                if (useWP) {
                    trueError[x][y] = pred[x][y] - (trueValue << 3);
                    for (int e = 0; e < 4; e++) {
                        int err = (Math.abs(subpred[e] - (trueValue << 3)) + 3) >> 3;
                        error[x][y][e] = err;
                    }
                }
            }
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

    public boolean isDecoded() {
        return decoded;
    }

    public static ModularChannel inverseHorizontalSqueeze(ModularChannelInfo info, ModularChannel orig, ModularChannel res) {
        if (info.width != orig.info.width + res.info.width || (orig.info.width != res.info.width && orig.info.width != 1 + res.info.width))
            throw new IllegalArgumentException("Corrupted squeeze transform");

        ModularChannel channel = new ModularChannel(info);
        for (int y = 0; y < info.height; y++) {
            for (int x = 0; x < res.info.width; x++) {
                int avg = orig.get(x, y);
                int residu = res.get(x, y);
                int nextAvg = x + 1 < orig.info.width ? orig.get(x + 1, y) : avg;
                int left = x > 0 ? channel.get((x << 1) - 1, y) : avg;
                int diff = residu + tendency(left, avg, nextAvg);
                int first = avg + diff / 2;
                channel.set(2 * x, y, first);
                channel.set(2 * x + 1, y, first - diff);
            }
            if (orig.info.width > res.info.width)
                channel.set(2 * res.info.width, y, orig.get(res.info.width, y));
        }

        return channel;
    }

    public static ModularChannel inverseVerticalSqueeze(ModularChannelInfo info, ModularChannel orig, ModularChannel res) {
        if (info.height != orig.info.height + res.info.height || (orig.info.height != res.info.height && orig.info.height != 1 + res.info.height))
            throw new IllegalStateException("Corrupted squeeze transform");

        ModularChannel channel = new ModularChannel(info);
        for (int x = 0; x < info.width; x++) {
            for (int y = 0; y < res.info.height; y++) {
                int avg = orig.get(x, y);
                int residu = res.get(x, y);
                int nextAvg = y + 1 < orig.info.height ? orig.get(x, y + 1) : avg;
                int top = y > 0 ? channel.get(x, (y << 1) - 1) : avg;
                int diff = residu + tendency(top, avg, nextAvg);
                int first = avg + diff / 2;
                channel.set(x, 2 * y, first);
                channel.set(x, 2 * y + 1, first - diff);
            }
            if (orig.info.height > res.info.height)
                channel.set(x, 2 * res.info.height, orig.get(x, res.info.height));
        }

        return channel;
    }

    public ModularChannelInfo getInfo() {
        return info;
    }
}
