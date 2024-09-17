package com.traneptora.jxlatte.frame.vardct;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.InvalidBitstreamException;
import com.traneptora.jxlatte.util.Point;

public class HFPass {

    private static final Point[][] naturalOrder = new Point[13][];

    private static Comparator<Point> getNaturalOrderComparator(int i) {
        return new Comparator<Point>() {
            public int compare(Point a, Point b) {
                TransformType tt = TransformType.getByOrderID(i);
                int maxDim = Math.max(tt.dctSelectHeight, tt.dctSelectWidth);
                boolean aLLF = a.y < tt.dctSelectHeight && a.x < tt.dctSelectWidth;
                boolean bLLF = b.y < tt.dctSelectHeight && b.x < tt.dctSelectWidth;
                if (aLLF && !bLLF)
                    return -1;
                if (bLLF && !aLLF)
                    return 1;
                if (aLLF && bLLF) {
                    if (b.y != a.y)
                        return a.y - b.y;
                    return a.x - b.x;
                }
                int aSY = a.y * maxDim / tt.dctSelectHeight;
                int aSX = a.x * maxDim / tt.dctSelectWidth;
                int bSY = b.y * maxDim / tt.dctSelectHeight;
                int bSX = b.x * maxDim / tt.dctSelectWidth;
                int aKey1 = aSY + aSX;
                int bKey1 = bSY + bSX;
                if (aKey1 != bKey1)
                    return aKey1 - bKey1;
                int aKey2 = aSX - aSY;
                int bKey2 = bSX - bSY;
                if ((aKey1 & 1) == 1)
                    aKey2 = -aKey2;
                if ((bKey1 & 1) == 1)
                    bKey2 = -bKey2;
                return aKey2 - bKey2;
            }
        };
    }

    private static Point[] getNaturalOrder(int i) {
        if (naturalOrder[i] != null)
            return naturalOrder[i];
        TransformType tt = TransformType.getByOrderID(i);
        int len = tt.pixelHeight * tt.pixelWidth;
        naturalOrder[i] = new Point[len];
        for (int y = 0; y < tt.pixelHeight; y++) {
            for (int x = 0; x < tt.pixelWidth; x++) {
                naturalOrder[i][y * tt.pixelWidth + x] = new Point(y, x);
            }
        }
        Arrays.sort(naturalOrder[i], getNaturalOrderComparator(i));
        return naturalOrder[i];
    }

    public final int usedOrders;
    public final Point[][][] order = new Point[13][3][];
    public final EntropyStream contextStream;

    public HFPass(Bitreader reader, Frame frame, int passIndex) throws IOException {
        usedOrders = reader.readU32(0x5F, 0, 0x13, 0, 0, 0, 0, 13);
        EntropyStream stream = usedOrders != 0 ? new EntropyStream(frame.getLoggers(), reader, 8) : null;
        for (int b = 0; b < 13; b++) {
            Point[] naturalOrder = getNaturalOrder(b);
            int len = naturalOrder.length;
            for (int c = 0; c < 3; c++) {
                if ((usedOrders & (1 << b)) != 0) {
                    order[b][c] = new Point[len];
                    int[] perm = Frame.readPermutation(reader, stream, len, len / 64);
                    for (int i = 0; i < order[b][c].length; i++)
                        order[b][c][i] = naturalOrder[perm[i]];
                } else {
                    order[b][c] = naturalOrder;
                }
            }
        }
        if (stream != null && !stream.validateFinalState())
            throw new InvalidBitstreamException("ANS state decoding HFPass perms: " + passIndex);
        int numContexts = 495 * frame.getHFGlobal().numHfPresets * frame.getLFGlobal().hfBlockCtx.numClusters;
        contextStream = new EntropyStream(frame.getLoggers(), reader, numContexts);
    }
}
