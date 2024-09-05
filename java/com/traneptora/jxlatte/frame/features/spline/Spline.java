package com.traneptora.jxlatte.frame.features.spline;

import java.util.ArrayList;
import java.util.List;

import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.LFGlobal;
import com.traneptora.jxlatte.util.ImageBuffer;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.Point;

public class Spline {
    public final Point[] controlPoints;
    private float[] upsampledY;
    private float[] upsampledX;
    private SplineArc[] arcs;
    private float[] coeffX;
    private float[] coeffY;
    private float[] coeffB;
    private float[] coeffSigma;
    private int splineID;

    public Spline(int splineID, Point[] controlPoints) {
        this.controlPoints = controlPoints;
    }

    private void upsampleControlPoints() {
        if (controlPoints.length == 1) {
            upsampledY = new float[controlPoints.length];
            upsampledX = new float[controlPoints.length];
            upsampledY[0] = controlPoints[0].y;
            upsampledX[0] = controlPoints[0].x;
            return;
        }

        Point[] extended = new Point[controlPoints.length + 2];
        extended[0] = new Point(controlPoints[0].y * 2 - controlPoints[1].y,
            controlPoints[0].x * 2 - controlPoints[1].x);
        for (int i = 0; i < controlPoints.length; i++)
            extended[i + 1] = new Point(controlPoints[i]);
        Point last = controlPoints[controlPoints.length - 1];
        Point penLast = controlPoints[controlPoints.length - 2];
        extended[extended.length - 1] = new Point(last.y * 2 - penLast.y, last.x * 2 - penLast.x);
        upsampledY = new float[16 * (extended.length - 3) + 1];
        upsampledX = new float[upsampledY.length];
        float[] t = new float[4];
        float[] pY = new float[4];
        float[] pX = new float[4];
        float[] dY = new float[3];
        float[] dX = new float[3];
        float[] aY = new float[3];
        float[] aX = new float[3];
        float[] bY = new float[2];
        float[] bX = new float[2];
        for (int i = 0; i < extended.length - 3; i++) {
            for (int k = 0; k < 4; k++) {
                pY[k] = extended[i + k].y;
                pX[k] = extended[i + k].x;
            }
            upsampledY[i << 4] = pY[1];
            upsampledX[i << 4] = pX[1];
            t[0] = 0.0f;
            for (int k = 0; k < 3; k++) {
                dY[k] = pY[k + 1] - pY[k];
                dX[k] = pX[k + 1] - pX[k];
                t[k + 1] = t[k] + (float)Math.pow(dY[k]*dY[k] + dX[k]*dX[k], 0.25D);
            }
            for (int step = 1; step < 16; step++) {
                float knot = t[1] + 0.0625f * step * (t[2] - t[1]);
                for (int k = 0; k < 3; k++) {
                    float f = (knot - t[k]) / (t[k + 1] - t[k]);
                    aY[k] = dY[k] * f + pY[k];
                    aX[k] = dX[k] * f + pX[k];
                }
                for (int k = 0; k < 2; k++) {
                    float f = (knot - t[k]) / (t[k + 2] - t[k]);
                    bY[k] = (aY[k + 1] - aY[k]) * f + aY[k];
                    bX[k] = (aX[k + 1] - aX[k]) * f + aX[k];
                }
                float f = (knot - t[1]) / (t[2] - t[1]);
                upsampledY[i * 16 + step] = (bY[1] - bY[0]) * f + bY[0];
                upsampledX[i * 16 + step] = (bX[1] - bX[0]) * f + bX[0];
            }
        }
        upsampledY[upsampledY.length - 1] = controlPoints[controlPoints.length - 1].y;
        upsampledX[upsampledY.length - 1] = controlPoints[controlPoints.length - 1].x;
    }

    private SplineArc[] computeIntermediarySamples(float renderDistance) {
        float currentY = upsampledY[0];
        float currentX = upsampledX[0];
        int nextID = 0;
        List<SplineArc> allSamples = new ArrayList<>();
        allSamples.add(new SplineArc(currentY, currentX, renderDistance));
        while (nextID < upsampledY.length) {
            float prevY = currentY;
            float prevX = currentX;
            float arcLengthFromPrevious = 0f;
            while (true) {
                if (nextID >= upsampledY.length) {
                    allSamples.add(new SplineArc(prevY, prevX, arcLengthFromPrevious));
                    break;
                }
                float nextY = upsampledY[nextID];
                float nextX = upsampledX[nextID];
                float dY = nextY - prevY;
                float dX = nextX - prevX;
                float arcLengthToNext = (float)Math.sqrt(dY * dY + dX * dX);
                if (arcLengthFromPrevious + arcLengthToNext >= renderDistance) {
                    float f = (renderDistance - arcLengthFromPrevious) / arcLengthToNext;
                    currentY = dY * f + prevY;
                    currentX = dX * f + prevX;
                    allSamples.add(new SplineArc(currentY, currentX, renderDistance));
                    break;
                }
                arcLengthFromPrevious += arcLengthToNext;
                prevY = nextY;
                prevX = nextX;
                nextID++;
            }
        }
        return allSamples.stream().toArray(SplineArc[]::new);
    }

    private static float fourierICT(float[] coeffs, float t) {
        float total = MathHelper.SQRT_H * coeffs[0];
        for (int i = 1; i < 32; i++) {
            total += coeffs[i] * (float)Math.cos(i * (Math.PI / 32D) * (t + 0.5D));
        }
        return total;
    }

    private void computeCoeffs(int splineID, LFGlobal lfGlobal) {
        coeffX = new float[32];
        coeffY = new float[32];
        coeffB = new float[32];
        coeffSigma = new float[32];
        float quantAdjust = lfGlobal.splines.quantAdjust / 8f;
        float invQa = quantAdjust >= 0 ? 1f / (1f + quantAdjust) : 1.0f - quantAdjust;
        float yAdjust = 0.106066017f * invQa;
        float xAdjust = 0.005939697f * invQa;
        float bAdjust = 0.098994949f * invQa;
        float sigmaAdjust = 0.47135738f * invQa;
        for (int i = 0; i < 32; i++) {
            coeffY[i] = lfGlobal.splines.coeffY[splineID][i] * yAdjust;
            coeffX[i] = lfGlobal.splines.coeffX[splineID][i] * xAdjust
                + lfGlobal.lfChanCorr.baseCorrelationX * coeffY[i];
            coeffB[i] = lfGlobal.splines.coeffB[splineID][i] * bAdjust
                + lfGlobal.lfChanCorr.baseCorrelationB * coeffY[i];
            coeffSigma[i] = lfGlobal.splines.coeffSigma[splineID][i] * sigmaAdjust;
        }
    }

    public void renderSpline(Frame frame) {
        computeCoeffs(splineID, frame.getLFGlobal());
        upsampleControlPoints();
        float renderDistance = 1.0f;
        arcs = computeIntermediarySamples(renderDistance);
        float arcLength = (arcs.length - 2f) * renderDistance + arcs[arcs.length - 1].arcLength;
        if (arcLength <= 0D)
            return;
        for (int i = 0; i < arcs.length; i++) {
            SplineArc arc = arcs[i];
            float progressAlongArc = Math.min(1.0f, i * renderDistance / arcLength);
            float t = 31f * progressAlongArc;
            float[] values = new float[3];
            values[0] = fourierICT(coeffX, t) * arc.arcLength;
            values[1] = fourierICT(coeffY, t) * arc.arcLength;
            values[2] = fourierICT(coeffB, t) * arc.arcLength;
            float sigma = fourierICT(coeffSigma, t);
            float inverseSigma = 1f / sigma;
            float maxColor = MathHelper.max(0.01f, values[0], values[1], values[2]);
            float maxDist = (float)Math.sqrt(-2f * sigma * sigma * ((float)Math.log(0.1D) * 3f - maxColor));
            int xBegin = Math.max(0, MathHelper.round(arc.locationX - maxDist));
            int xEnd = Math.min(frame.getFrameHeader().bounds.size.width - 1,
                MathHelper.round(arc.locationX + maxDist));
            int yBegin = Math.max(0, MathHelper.round(arc.locationY - maxDist));
            int yEnd = Math.min(frame.getFrameHeader().bounds.size.height - 1,
                MathHelper.round(arc.locationY + maxDist));
            for (int c = 0; c < 3; c++) {
                ImageBuffer buffer = frame.getBuffer()[c];
                buffer.castToFloatIfInt(~(~0 << frame.globalMetadata.getBitDepthHeader().bitsPerSample));
                float[][] fb = buffer.getFloatBuffer();
                for (int y = yBegin; y <= yEnd; y++) {
                    float[] fby = fb[y];
                    for (int x = xBegin; x <= xEnd; x++) {
                        float dY = y - arc.locationY;
                        float dX = x - arc.locationX;
                        float distance = (float)Math.sqrt(dY * dY + dX * dX);
                        float factor = MathHelper.erf((0.5f * distance + MathHelper.SQRT_F) * inverseSigma);
                        factor -= MathHelper.erf((0.5f * distance - MathHelper.SQRT_F) * inverseSigma);
                        float extra = 0.25f * values[c] * sigma * factor * factor;
                        synchronized (buffer) {
                            fby[x] += extra;
                        }
                    }
                }
            }
        }
    }
}
