package com.thebombzen.jxlatte.frame.vardct;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.IntPoint;

public class HFPass {

    private static final IntPoint[][] naturalOrder = new IntPoint[13][];

    static {
        for (int i = 0; i < 13; i++) {
            final int index = i;
            TransformType tt = Stream.of(TransformType.values())
                .filter(t -> t.orderID == index && !t.isVertical()).findAny().get();
            int ch = tt.blockHeight / 8;
            int cw = tt.blockWidth / 8;

            IntPoint[] coeffs = new IntPoint[tt.blockHeight * tt.blockWidth];

            int coeffIdx = 0;
            for (int y = 0; y < ch; y++) {
                for (int x = 0; x < cw; x++) {
                    coeffs[coeffIdx++] = new IntPoint(x, y);
                }
            }

            Function<IntPoint, IntPoint> keys = (p) -> {
                int sx = p.x * Math.max(cw, ch) / cw;
                int sy = p.y * Math.max(cw, ch) / ch;
                int k1 = sx + sy;
                int k2 = sx - sy;
                if ((k1 & 1) == 1)
                    k2 = -k2;
                return new IntPoint(k1, k2);
            };

            Comparator<IntPoint> alphabetical = (p1, p2) -> {
                int c = Integer.compare(p1.x, p2.x);
                return c != 0 ? c : Integer.compare(p1.y, p2.y);
            };

            Comparator<IntPoint> sorting = (p1, p2) -> {
                return alphabetical.compare(keys.apply(p1), keys.apply(p2));
            };

            for (int y = 0; y < tt.blockHeight; y++) {
                for (int x = 0; x < tt.blockWidth; x++) {
                    if (x < cw && y < ch)
                        continue;
                    coeffs[coeffIdx++] = new IntPoint(x, y);
                }
            }

            Arrays.sort(coeffs, cw * ch, coeffs.length, sorting);
            naturalOrder[index] = coeffs;
        }
    }

    public final int usedOrders;
    public final IntPoint[][][] order = new IntPoint[13][3][];
    public final EntropyStream contextStream;

    public HFPass(Bitreader reader, Frame frame, int passIndex) throws IOException {
        usedOrders = reader.readU32(0x5F, 0, 0x13, 0, 0, 0, 0, 13);
        EntropyStream stream = usedOrders != 0 ? new EntropyStream(reader, 8) : null;
        for (int b = 0; b < 13; b++) {
            for (int c = 0; c < 3; c++) {
                order[b][c] = new IntPoint[naturalOrder[b].length];
                if ((usedOrders & (1 << b)) != 0) {
                    int[] perm = Frame.readPermutation(reader, stream, order[b][c].length, order[b][c].length / 64);
                    for (int i = 0; i < order[b][c].length; i++)
                        order[b][c][i] = naturalOrder[b][perm[i]];
                } else {
                    for (int i = 0; i < order[b][c].length; i++)
                        order[b][c][i] = naturalOrder[b][i];
                }
            }
        }
        if (stream != null && !stream.validateFinalState())
            throw new InvalidBitstreamException("ANS state decoding HFPass perms: " + passIndex);
        int numContexts = 495 * frame.getHFGlobal().numHfPresets * frame.getLFGlobal().hfBlockCtx.numClusters;
        contextStream = new EntropyStream(reader, numContexts);
    }
}
