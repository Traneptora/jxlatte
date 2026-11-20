package com.traneptora.jxlatte.frame.modular;

import java.io.IOException;
import java.util.function.IntUnaryOperator;

import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.Loggers;
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

    public static ModularChannel copyOf(ModularChannel channel) {
        ModularChannel ret = new ModularChannel(channel.size.height, channel.size.width,
                channel.vshift, channel.hshift, channel.forceWP);
        ret.origin = new Point(channel.origin);

        ret.decoded = channel.decoded;
        if (channel.buffer != null) {
            ret.allocate();
            for (int y = 0; y < ret.size.height; y++)
                System.arraycopy(channel.buffer[y], 0, ret.buffer[y], 0, ret.size.width);
        }

        return ret;
    }

    public int[][] buffer;
    protected long[][][] error;
    protected int[][] trueError;
    protected long[][] pred;
    private long[] subpred;
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

    public void allocate() {
        if (buffer != null)
            return;
        if (size.height == 0 || size.width == 0) {
            buffer = new int[0][];
        } else {
            buffer = new int[size.height][size.width];
        }
    }

    private static long west(int[][] buffer, int y, int x) {
        return x > 0 ? buffer[y][x - 1] : y > 0 ? buffer[y - 1][x] : 0;
    }

    private static long north(int[][] buffer, int y, int x) {
        return y > 0 ? buffer[y - 1][x] : x > 0 ? buffer[y][x - 1] : 0;
    }

    private static long northWest(int[][] buffer, int y, int x) {
        return x > 0 ? (y > 0 ? buffer[y - 1][x - 1] : buffer[y][x - 1]) : (y > 0 ? buffer[y - 1][x] : 0);
    }

    private static long northEast(int[][] buffer, int y, int x) {
        return x + 1 < buffer[y].length && y > 0 ? buffer[y - 1][x + 1] : north(buffer, y, x);
    }

    private static long northNorth(int[][] buffer, int y, int x) {
        return y > 1 ? buffer[y - 2][x] : north(buffer, y, x);
    }

    private static long northEastEast(int[][] buffer, int y, int x) {
        return x + 2 < buffer[y].length && y > 0 ? buffer[y - 1][x + 2] : northEast(buffer, y, x);
    }

    private static long westWest(int[][] buffer, int y, int x) {
        return x > 1 ? buffer[y][x - 2] : west(buffer, y, x);
    }

    private static long errorNorth(long[][] buffer, int y, int x) {
        return buffer[~y & 1][x];
    }

    private static long errorWest(long[][] buffer, int y, int x) {
        return x != 0 ? buffer[y & 1][x - 1] : 0;
    }

    private static long errorWestWest(long[][] buffer, int y, int x) {
        return x > 1 ? buffer[y & 1][x - 2] : 0;
    }

    private static long errorNorthWest(long[][] buffer, int y, int x) {
        return buffer[~y & 1][x != 0 ? x - 1: 0];
    }

    private static long errorNorthEast(long[][] buffer, int y, int x) {
        long[] row = buffer[~y & 1];
        return row[x + 1 != row.length ? x + 1 : x];
    }

    private static int errorNorth(int[][] buffer, int y, int x) {
        return buffer[~y & 1][x];
    }

    private static int errorWest(int[][] buffer, int y, int x) {
        return x != 0 ? buffer[y & 1][x - 1] : 0;
    }

    private static int errorNorthWest(int[][] buffer, int y, int x) {
        return buffer[~y & 1][x != 0 ? x - 1 : 0];
    }

    private static int errorNorthEast(int[][] buffer, int y, int x) {
        int[] row = buffer[~y & 1];
        return row[x + 1 != row.length ? x + 1 : x];
    }

    protected long prediction(int y, int x, int k) {
        long n, v, nw, w;
        switch (k) {
            case 0:
                return 0;
            case 1:
                return west(buffer, y, x);
            case 2:
                return north(buffer, y, x);
            case 3:
                return (west(buffer, y, x) + north(buffer, y, x)) / 2L;
            case 4:
                w = west(buffer, y, x);
                n = north(buffer, y, x);
                nw = northWest(buffer, y, x);
                return Math.abs(n - nw) < Math.abs(w - nw) ? w : n;
            case 5:
                w = west(buffer, y, x);
                n = north(buffer, y, x);
                v = w + n - northWest(buffer, y, x);
                return MathHelper.clamp(v, n, w);
            case 6:
                return (pred[y][x] + 3L) >> 3;
            case 7:
                return northEast(buffer, y, x);
            case 8:
                return northWest(buffer, y, x);
            case 9:
                return westWest(buffer, y, x);
            case 10:
                return (west(buffer, y, x) + northWest(buffer, y, x)) / 2L;
            case 11:
                return (north(buffer, y, x) + northWest(buffer, y, x)) / 2L;
            case 12:
                return (north(buffer, y, x) + northEast(buffer, y, x)) / 2L;
            case 13:
                return (  6L * north(buffer, y, x)
                        - 2L * northNorth(buffer, y, x)
                        + 7L * west(buffer, y, x)
                        + westWest(buffer, y, x)
                        + northEastEast(buffer, y, x)
                        + 3L * northEast(buffer, y, x)
                        + 8L) / 16L;
            default:
                throw new IllegalStateException();
        }
    }

    private int prePredictWP(WPParams wpParams, int y, int x) {
        long n3 = north(buffer, y, x) << 3;
        long nw3 = northWest(buffer, y, x) << 3;
        long ne3 = northEast(buffer, y, x) << 3;
        long w3 = west(buffer, y, x) << 3;
        long nn3 = northNorth(buffer, y, x) << 3;
        long tN = errorNorth(trueError, y, x);
        long tW = errorWest(trueError, y, x);
        long tNE = errorNorthEast(trueError, y, x);
        long tNW = errorNorthWest(trueError, y, x);
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
            long eSum = errorNorth(error[e], y, x) + errorWest(error[e], y, x) + errorNorthWest(error[e], y, x)
                + errorWestWest(error[e], y, x) + errorNorthEast(error[e], y, x);
            if (x + 1 == size.width)
                eSum += errorWest(error[e], y, x);
            eSum &= 0xffffffffL;
            int shift = MathHelper.floorLog1p(eSum) - 5;
            if (shift < 0)
                shift = 0;
            weight[e] = 4 + ((wpParams.weight[e] * oneL24OverKP1[(int)(eSum >>> shift)]) >>> shift);
            wSum += weight[e];
        }
        int logWeight = MathHelper.floorLog1p(wSum - 1) - 4;
        wSum = 0;
        for (int e = 0; e < 4; e++) {
            weight[e] >>>= logWeight;
            wSum += weight[e];
        }
        long s = (wSum >>> 1) - 1L;
        for (int e = 0; e < 4; e++)
            s += subpred[e] * weight[e];
        pred[y][x] = (s * oneL24OverKP1[wSum - 1]) >> 24;
        if (((tN ^ tW) | (tN ^ tNW)) <= 0)
            pred[y][x] = MathHelper.clamp(pred[y][x], w3, n3, ne3);
        long maxError = tW;
        if (Math.abs(tN) > Math.abs(maxError))
            maxError = tN;
        if (Math.abs(tNW) > Math.abs(maxError))
            maxError = tNW;
        if (Math.abs(tNE) > Math.abs(maxError))
            maxError = tNE;
        return (int) maxError;
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
                return (int) Math.abs(north(buffer, y, x));
            case 5:
                return (int) Math.abs(west(buffer, y, x));
            case 6:
                return (int) north(buffer, y, x);
            case 7:
                return (int) west(buffer, y, x);
            case 8:
                return (int) (x > 0
                    ? west(buffer, y, x) - (west(buffer, y, x - 1) + north(buffer, y, x - 1) - northWest(buffer, y, x - 1))
                    : west(buffer, y, x));
            case 9:
                return (int) (west(buffer, y, x) + north(buffer, y, x) - northWest(buffer, y, x));
            case 10:
                return (int) (west(buffer, y, x) - northWest(buffer, y, x));
            case 11:
                return (int) (northWest(buffer, y, x) - north(buffer, y, x));
            case 12:
                return (int) (north(buffer, y, x) - northEast(buffer, y, x));
            case 13:
                return (int) (north(buffer, y, x) - northNorth(buffer, y, x));
            case 14:
                return (int) (west(buffer, y, x) - westWest(buffer, y, x));
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
                    long rC = channel.buffer[y][x];
                    if (k2++ == k)
                        return (int) Math.abs(rC);
                    if (k2++ == k)
                        return (int) rC;
                    long rW = x > 0 ? channel.buffer[y][x - 1] : 0;
                    long rN = y > 0 ? channel.buffer[y - 1][x] : rW;
                    long rNW = x > 0 && y > 0 ? channel.buffer[y - 1][x - 1] : rW;
                    long rG = rC - MathHelper.clamp(rW + rN - rNW, rN, rW);
                    if (k2++ == k)
                        return (int) Math.abs(rG);
                    if (k2++ == k)
                        return (int) rG;
                }

                return 0;
        }
    }

    private IntUnaryOperator getWalkFunction(ModularStream parent, int channelIndex,
            int streamIndex, WPParams wpParams, int y, int x) {
        int maxError;
        if (wpParams != null)
            maxError = prePredictWP(wpParams, y, x);
        else
            maxError = 0;
        return k -> propertyExpand(parent, channelIndex, streamIndex, wpParams, k, maxError, y, x);
    }

    public boolean decode(Bitreader reader, EntropyStream stream, WPParams wpParams, MATree tree,
            ModularStream parent, int channelIndex, int streamIndex, int distMultiplier, Loggers loggers)
            throws IOException {
        if (decoded)
            throw new IllegalStateException("Channel decoded twice");
        decoded = true;
        allocate();
        boolean useWP = forceWP || tree.usesWeightedPredictor();
        if (useWP) {
            error = new long[4][2][size.width];
            trueError = new int[2][size.width];
            pred = new long[size.height][size.width];
            subpred = new long[4];
            weight = new int[4];
        }
        if (!useWP)
            wpParams = null;
        for (int y = 0; y < size.height; y++) {
            MATree refinedTree = tree.compactify(channelIndex, streamIndex, y);
            for (int x = 0; x < size.width; x++) {
                MATree leafNode = refinedTree.walk(getWalkFunction(parent, channelIndex, streamIndex, wpParams, y, x));
                int packedDiff;
                try {
                    int context = leafNode.getContext();
                    packedDiff = stream.readSymbol(reader, context, distMultiplier);
                } catch (IOException ioe) {
                    loggers.log(Loggers.LOG_VERBOSE, "Error at y=%d, x=%d, h=%d, w=%d, c=%d, s=%d%n", y, x,
                        size.height, size.width, channelIndex, streamIndex);
                    throw ioe;
                }
                int diff = MathHelper.unpackSigned(packedDiff) * leafNode.getMultiplier() + leafNode.getOffset();
                int trueValue = diff + (int) prediction(y, x, leafNode.getPredictor());
                buffer[y][x] = trueValue;
                if (useWP) {
                    long v3 = ((long) trueValue) << 3;
                    for (int e = 0; e < 4; e++) {
                        error[e][y & 1][x] = (Math.abs(subpred[e] - v3) + 3L) >> 3;
                    }
                    trueError[y & 1][x] = (int)(pred[y][x] - v3);
                }
            }
        }
        return true;
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
            System.arraycopy(orig.buffer[res.size.height], 0, channel.buffer[2*res.size.height],
                0, channel.size.width);

        return channel;
    }

    @Override
    public String toString() {
        return String.format("%s [decoded=%s, size=%s, vshift=%s, hshift=%s, origin=%s, forceWP=%s]",
            "ModularChannel", decoded, size, vshift, hshift, origin, forceWP);
    }
}
