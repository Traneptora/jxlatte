package com.traneptora.jxlatte.frame.modular;

import java.io.IOException;
import java.util.function.IntUnaryOperator;

import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.Dimension;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.Point;

public class ModularChannel {

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

    public Dimension size;
    public int vshift;
    public int hshift;
    public Point origin = new Point();
    protected boolean forceWP;

    public ModularChannel(int height, int width, int vshift, int hshift) {
        this(height, width, vshift, hshift, false);
    }

    public ModularChannel(int height, int width, int vshift, int hshift, boolean forceWP) {
        this.size = new Dimension(height, width);
        this.vshift = vshift;
        this.hshift = hshift;
        this.forceWP = forceWP;
    }

    public ModularChannel(ModularChannel channel) {
        this(channel.size.height, channel.size.width, channel.vshift, channel.hshift, channel.forceWP);
        this.origin = new Point(channel.origin);

        decoded = channel.decoded;
        if (channel.buffer != null) {
            allocate();
            for (int y = 0; y < size.height; y++)
                System.arraycopy(channel.buffer[y], 0, buffer[y], 0, size.width);
        }
    }

    public void allocate() {
        if (buffer != null)
            return;
        if (size.height == 0 || size.width == 0) {
            buffer = new int[0][];
        } else {
            buffer = new int[size.height][size.width];
        }
    }

    private int west(int x, int y) {
        return x > 0 ? buffer[y][x - 1] : y > 0 ? buffer[y - 1][x] : 0;
    }

    private int north(int x, int y) {
        return y > 0 ? buffer[y - 1][x] : x > 0 ? buffer[y][x - 1] : 0;
    }

    private int northWest(int x, int y) {
        return x > 0 ? (y > 0 ? buffer[y - 1][x - 1] : buffer[y][x - 1]) : (y > 0 ? buffer[y - 1][x] : 0);
    }

    private int northEast(int x, int y) {
        return x + 1 < size.width && y > 0 ? buffer[y - 1][x + 1] : north(x, y);
    }

    private int northNorth(int x, int y) {
        return y > 1 ? buffer[y - 2][x] : north(x, y);
    }

    private int northEastEast(int x, int y) {
        return x + 2 < size.width && y > 0 ? buffer[y - 1][x + 2] : northEast(x, y);
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
        return x + 1 < size.width && y > 0 ? error[e][y - 1][x + 1] : errorNorth(x, y, e);
    }

    protected int prediction(int y, int x, int k) {
        int n, v, nw, w;
        switch (k) {
            case 0:
                return 0;
            case 1:
                return x > 0 ? buffer[y][x - 1] : y > 0 ? buffer[y - 1][x] : 0;
            case 2:
                return y > 0 ? buffer[y - 1][x] : x > 0 ? buffer[y][x - 1] : 0;
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
            if (x + 1 == size.width)
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

    private int propertyExpand(ModularStream parent, int channelIndex, int streamIndex,
            WPParams wpParams, int k, int maxError, int y, int x) {
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
                    if (!size.equals(channel.size) || vshift != channel.vshift || hshift != channel.hshift)
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
    }

    private IntUnaryOperator getWalkFunction(ModularStream parent, int channelIndex,
            int streamIndex, WPParams wpParams, int y, int x) {
        int maxError;
        if (wpParams != null)
            maxError = prePredictWP(wpParams, x, y);
        else
            maxError = 0;
        return k -> propertyExpand(parent, channelIndex, streamIndex, wpParams, k, maxError, y, x);
    }

    public void decode(Bitreader reader, EntropyStream stream, WPParams wpParams, MATree tree,
            ModularStream parent, int channelIndex, int streamIndex, int distMultiplier) throws IOException {
        if (decoded)
            throw new IllegalStateException("Channel decoded twice");
        decoded = true;
        allocate();
        tree = tree.compactify(channelIndex, streamIndex);
        boolean useWP = forceWP || tree.usesWeightedPredictor();
        if (useWP) {
            error = new int[5][size.height][size.width];
            pred = new int[size.height][size.width];
            subpred = new int[4];
            weight = new int[4];
        }
        if (!useWP)
            wpParams = null;
        for (int y = 0; y < size.height; y++) {
            MATree refinedTree = tree.compactify(channelIndex, streamIndex, y);
            for (int x = 0; x < size.width; x++) {
                MATree leafNode = refinedTree.walk(getWalkFunction(parent, channelIndex, streamIndex, wpParams, y, x));
                int diff = stream.readSymbol(reader, leafNode.getContext(), distMultiplier);
                diff = MathHelper.unpackSigned(diff) * leafNode.getMultiplier() + leafNode.getOffset();
                int trueValue = diff + prediction(y, x, leafNode.getPredictor());
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

    public static ModularChannel inverseHorizontalSqueeze(ModularChannel channel,
            ModularChannel orig, ModularChannel res) {
        if (channel.size.width != orig.size.width + res.size.width
                || (orig.size.width != res.size.width && orig.size.width != 1 + res.size.width)
                || channel.size.height != orig.size.height || res.size.height != orig.size.height)
            throw new IllegalArgumentException("Corrupted squeeze transform");
        channel.allocate();
        for (int y = 0; y < channel.size.height; y++) {
            for (int x = 0; x < res.size.width; x++) {
                int avg = orig.buffer[y][x];
                int residu = res.buffer[y][x];
                int nextAvg = x + 1 < orig.size.width ? orig.buffer[y][x + 1] : avg;
                int left = x > 0 ? channel.buffer[y][2*x - 1] : avg;
                int diff = residu + tendency(left, avg, nextAvg);
                int first = avg + diff / 2;
                channel.buffer[y][2*x] = first;
                channel.buffer[y][2*x + 1] = first - diff;
            }
        }
        if (orig.size.width > res.size.width) {
            final int xs = 2 * res.size.width;
            for (int y = 0; y < channel.size.height; y++)
                channel.buffer[y][xs] = orig.buffer[y][res.size.width];
        }

        return channel;
    }

    public static ModularChannel inverseVerticalSqueeze(ModularChannel channel,
            ModularChannel orig, ModularChannel res) {
        if (channel.size.height != orig.size.height + res.size.height
                || (orig.size.height != res.size.height && orig.size.height != 1 + res.size.height)
                || channel.size.width != orig.size.width || res.size.width != orig.size.width)
            throw new IllegalStateException("Corrupted squeeze transform");

        channel.allocate();
        for (int y = 0; y < res.size.height; y++) {
            for (int x = 0; x < channel.size.width; x++) {
                int avg = orig.buffer[y][x];
                int residu = res.buffer[y][x];
                int nextAvg = y + 1 < orig.size.height ? orig.buffer[y + 1][x] : avg;
                int top = y > 0 ? channel.buffer[2*y - 1][x] : avg;
                int diff = residu + tendency(top, avg, nextAvg);
                int first = avg + diff / 2;
                channel.buffer[2*y][x] = first;
                channel.buffer[2*y + 1][x] = first - diff;
            }
        }
        if (orig.size.height > res.size.height)
            System.arraycopy(orig.buffer[res.size.height], 0, channel.buffer[2*res.size.height], 0, channel.size.width);

        return channel;
    }

    @Override
    public String toString() {
        return String.format("ModularChannel [decoded=%s, size=%s, vshift=%s, hshift=%s, origin=%s, forceWP=%s]",
                decoded, size, vshift, hshift, origin, forceWP);
    }
}
