package com.thebombzen.jxlatte.frame.features.spline;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.IntPoint;
import com.thebombzen.jxlatte.util.MathHelper;

public class SplinesBundle {

    public final int numSplines;
    public final int quantAdjust;
    public final IntPoint[] splinePos;
    public final int[] controlCount;
    public final IntPoint[][] controlPoints;
    public final int[][] coeffX;
    public final int[][] coeffY;
    public final int[][] coeffB;
    public final int[][] coeffSigma;

    public SplinesBundle(Bitreader reader) throws IOException {
        EntropyStream stream = new EntropyStream(reader, 6);
        numSplines = 1 + stream.readSymbol(reader, 2);
        splinePos = new IntPoint[numSplines];
        for (int i = 0; i < numSplines; i++) {
            int x = stream.readSymbol(reader, 1);
            int y = stream.readSymbol(reader, 1);
            if (i != 0) {
                x = MathHelper.unpackSigned(x) + splinePos[i - 1].x;
                y = MathHelper.unpackSigned(y) + splinePos[i - 1].y;
            }
            splinePos[i] = new IntPoint(x, y);
        }
        quantAdjust = MathHelper.unpackSigned(stream.readSymbol(reader, 0));
        controlCount = new int[numSplines];
        controlPoints = new IntPoint[numSplines][];
        coeffX = new int[numSplines][32];
        coeffY = new int[numSplines][32];
        coeffB = new int[numSplines][32];
        coeffSigma = new int[numSplines][32];
        for (int i = 0; i < numSplines; i++) {
            controlCount[i] = 1 + stream.readSymbol(reader, 3);
            controlPoints[i] = new IntPoint[controlCount[i]];
            controlPoints[i][0] = new IntPoint(splinePos[i]);
            IntPoint[] delta = new IntPoint[controlCount[i] - 1];
            for (int j = 0; j < delta.length; j++) {
                int x = MathHelper.unpackSigned(stream.readSymbol(reader, 4));
                int y = MathHelper.unpackSigned(stream.readSymbol(reader, 4));
                delta[j] = new IntPoint(x, y);
            }
            IntPoint current = new IntPoint(controlPoints[i][0]);
            IntPoint deltaPoint = new IntPoint();
            for (int j = 1; j < controlCount[i]; j++) {
                deltaPoint = deltaPoint.plus(delta[j - 1]);
                current = current.plus(deltaPoint);
                controlPoints[i][j] = new IntPoint(current);
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
