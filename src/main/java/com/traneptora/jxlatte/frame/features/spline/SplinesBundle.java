package com.traneptora.jxlatte.frame.features.spline;

import java.io.IOException;

import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.InvalidBitstreamException;
import com.traneptora.jxlatte.io.Loggers;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.Point;

public class SplinesBundle {

    public final int numSplines;
    public final int quantAdjust;
    public final Point[] splinePos;
    public final int[] controlCount;
    public final Point[][] controlPoints;
    public final int[][] coeffX;
    public final int[][] coeffY;
    public final int[][] coeffB;
    public final int[][] coeffSigma;

    public SplinesBundle(Loggers loggers, Bitreader reader) throws IOException {
        EntropyStream stream = new EntropyStream(loggers, reader, 6);
        numSplines = 1 + stream.readSymbol(reader, 2);
        splinePos = new Point[numSplines];
        for (int i = 0; i < numSplines; i++) {
            int x = stream.readSymbol(reader, 1);
            int y = stream.readSymbol(reader, 1);
            if (i != 0) {
                x = MathHelper.unpackSigned(x) + splinePos[i - 1].x;
                y = MathHelper.unpackSigned(y) + splinePos[i - 1].y;
            }
            splinePos[i] = new Point(y, x);
        }
        quantAdjust = MathHelper.unpackSigned(stream.readSymbol(reader, 0));
        controlCount = new int[numSplines];
        controlPoints = new Point[numSplines][];
        coeffX = new int[numSplines][32];
        coeffY = new int[numSplines][32];
        coeffB = new int[numSplines][32];
        coeffSigma = new int[numSplines][32];
        for (int i = 0; i < numSplines; i++) {
            controlCount[i] = 1 + stream.readSymbol(reader, 3);
            controlPoints[i] = new Point[controlCount[i]];
            controlPoints[i][0] = new Point(splinePos[i]);
            int[] deltaY = new int[controlCount[i] - 1];
            int[] deltaX = new int[deltaY.length];
            for (int j = 0; j < deltaY.length; j++) {
                deltaX[j] = MathHelper.unpackSigned(stream.readSymbol(reader, 4));
                deltaY[j] = MathHelper.unpackSigned(stream.readSymbol(reader, 4));
            }
            int cY = controlPoints[i][0].y;
            int cX = controlPoints[i][0].x;
            int dY = 0, dX = 0;
            for (int j = 1; j < controlCount[i]; j++) {
                dY += deltaY[j - 1];
                dX += deltaX[j - 1];
                cY += dY;
                cX += dX;
                controlPoints[i][j] = new Point(cY, cX);
            }
            for (int j = 0; j < 32; j++)
                coeffX[i][j] = MathHelper.unpackSigned(stream.readSymbol(reader, 5));
            for (int j = 0; j < 32; j++)
                coeffY[i][j] = MathHelper.unpackSigned(stream.readSymbol(reader, 5));
            for (int j = 0; j < 32; j++)
                coeffB[i][j] = MathHelper.unpackSigned(stream.readSymbol(reader, 5));
            for (int j = 0; j < 32; j++)
                coeffSigma[i][j] = MathHelper.unpackSigned(stream.readSymbol(reader, 5));
        }
        if (!stream.validateFinalState())
            throw new InvalidBitstreamException("Illegal final ANS state");
    }
}
