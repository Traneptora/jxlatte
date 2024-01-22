package com.traneptora.jxlatte.frame.vardct;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.IntPoint;
import com.traneptora.jxlatte.util.functional.ExceptionalRunnable;
import com.traneptora.jxlatte.util.functional.FunctionalHelper;

public class HFPass {

    private static final int[][] naturalOrderX = new int[13][];
    private static final int[][] naturalOrderY = new int[13][];

    static {
        InputStream in = new BufferedInputStream(HFPass.class.getResourceAsStream("/natural-order.dat"));
        try {
            for (int i = 0; i < 13; i++) {
                final int index = i;
                TransformType tt = Stream.of(TransformType.values())
                    .filter(t -> t.orderID == index && !t.isVertical()).findAny().get();
                int len = tt.blockHeight * tt.blockWidth;
                naturalOrderX[index] = new int[len];
                naturalOrderY[index] = new int[len];
                naturalOrderX[index][0] = in.read();
                for (int j = 1; j < len; j++)
                    naturalOrderX[index][j] = (in.read() + naturalOrderX[index][j - 1]) & 0xFF;
                naturalOrderY[index][0] = in.read();
                for (int j = 1; j < len; j++)
                    naturalOrderY[index][j] = (in.read() + naturalOrderY[index][j - 1]) & 0xFF;
            }
        } catch (IOException ioe) {
            FunctionalHelper.sneakyThrow(ioe);
        } finally {
            ExceptionalRunnable.of(in::close).run();
        }
    }

    public final int usedOrders;
    public final IntPoint[][][] order = new IntPoint[13][3][];
    public final EntropyStream contextStream;

    public HFPass(Bitreader reader, Frame frame, int passIndex) throws IOException {
        usedOrders = reader.readU32(0x5F, 0, 0x13, 0, 0, 0, 0, 13);
        EntropyStream stream = usedOrders != 0 ? new EntropyStream(frame.getLoggers(), reader, 8) : null;
        for (int b = 0; b < 13; b++) {
            for (int c = 0; c < 3; c++) {
                order[b][c] = new IntPoint[naturalOrderX[b].length];
                if ((usedOrders & (1 << b)) != 0) {
                    int[] perm = Frame.readPermutation(reader, stream, order[b][c].length, order[b][c].length / 64);
                    for (int i = 0; i < order[b][c].length; i++) {
                        int x = naturalOrderX[b][perm[i]];
                        int y = naturalOrderY[b][perm[i]];
                        order[b][c][i] = new IntPoint(x, y);
                    }
                } else {
                    for (int i = 0; i < order[b][c].length; i++) {
                        int x = naturalOrderX[b][i];
                        int y = naturalOrderY[b][i];
                        order[b][c][i] = new IntPoint(x, y);
                    }
                }
            }
        }
        if (stream != null && !stream.validateFinalState())
            throw new InvalidBitstreamException("ANS state decoding HFPass perms: " + passIndex);
        int numContexts = 495 * frame.getHFGlobal().numHfPresets * frame.getLFGlobal().hfBlockCtx.numClusters;
        contextStream = new EntropyStream(frame.getLoggers(), reader, numContexts);
    }
}
