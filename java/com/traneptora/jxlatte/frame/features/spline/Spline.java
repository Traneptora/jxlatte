package com.traneptora.jxlatte.frame.features.spline;

import java.util.ArrayList;
import java.util.List;

import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.LFGlobal;
import com.traneptora.jxlatte.util.FlowHelper;
import com.traneptora.jxlatte.util.IntPoint;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.TaskList;

public class Spline {
    public final IntPoint[] controlPoints;
    private FloatPoint[] upsampled;
    private SplineArc[] arcs;
    private float[] coeffX;
    private float[] coeffY;
    private float[] coeffB;
    private float[] coeffSigma;
    private int splineID;

    public Spline(int splineID, IntPoint[] controlPoints) {
        this.controlPoints = controlPoints;
    }

    private FloatPoint[] upsampleControlPoints(FlowHelper flowHelper) {
        if (controlPoints.length == 1) {
            return new FloatPoint[]{new FloatPoint(controlPoints[0])};
        }
        IntPoint[] extended = new IntPoint[controlPoints.length + 2];
        extended[0] = controlPoints[0].plus(controlPoints[0]).minus(controlPoints[1]);
        for (int i = 0; i < controlPoints.length; i++) {
            extended[i + 1] = controlPoints[i];
        }
        extended[extended.length - 1] = controlPoints[controlPoints.length - 1].plus(controlPoints[controlPoints.length - 1]).minus(controlPoints[controlPoints.length - 2]);
        FloatPoint[] upsampled = new FloatPoint[16 * (extended.length - 3) + 1];
        TaskList<Void> tasks = new TaskList<>(flowHelper.getThreadPool());
        for (int i = 0; i < extended.length - 3; i++) {
            final int j = i;
            tasks.submit(() -> {
                float[] t = new float[4];
                FloatPoint[] p = new FloatPoint[4];
                FloatPoint[] a = new FloatPoint[3];
                FloatPoint[] b = new FloatPoint[3];
                for (int k = 0; k < 4; k++)
                    p[k] = new FloatPoint(extended[j + k]);
                upsampled[j * 16] = p[1];
                t[0] = 0f;
                for (int k = 1; k < 4; k++)
                    t[k] = t[k - 1] + (float)Math.pow(p[k].minus(p[k - 1]).normSquared(), 0.25D);
                for (int step = 1; step < 16; step++) {
                    float knot = t[1] + 0.0625f * step * (t[2] - t[1]);
                    for (int k = 0; k < 3; k++) {
                        float f = (knot - t[k]) / (t[k + 1] - t[k]);
                        a[k] = p[k + 1].minus(p[k]).times(f).plus(p[k]);
                    }
                    for (int k = 0; k < 2; k++) {
                        float f = (knot - t[k]) / (t[k + 2] - t[k]);
                        b[k] = a[k + 1].minus(a[k]).times(f).plus(a[k]);
                    }
                    float f = (knot - t[1]) / (t[2] - t[1]);
                    upsampled[j * 16 + step] = b[1].minus(b[0]).times(f).plus(b[0]);
                }
            });
        }
        upsampled[upsampled.length - 1] = new FloatPoint(controlPoints[controlPoints.length - 1]);
        tasks.collect();
        return upsampled;
    }

    private SplineArc[] computeIntermediarySamples(float renderDistance) {
        FloatPoint current = upsampled[0];
        int nextID = 0;
        List<SplineArc> allSamples = new ArrayList<>();
        allSamples.add(new SplineArc(current, renderDistance));
        while (nextID < upsampled.length) {
            FloatPoint prev = new FloatPoint(current);
            float arcLengthFromPrevious = 0f;
            while (true) {
                if (nextID >= upsampled.length) {
                    allSamples.add(new SplineArc(prev, arcLengthFromPrevious));
                    break;
                }
                FloatPoint next = upsampled[nextID];
                float arcLengthToNext = next.minus(prev).norm();
                if (arcLengthFromPrevious + arcLengthToNext >= renderDistance) {
                    float f = (renderDistance - arcLengthFromPrevious) / arcLengthToNext;
                    current = next.minus(prev).times(f).plus(prev);
                    allSamples.add(new SplineArc(current, renderDistance));
                    break;
                }
                arcLengthFromPrevious += arcLengthToNext;
                prev = next;
                nextID++;
            }
        }
        return allSamples.stream().toArray(SplineArc[]::new);
    }

    private static float fourierICT(float[] coeffs, float t) {
        float total = MathHelper.SQRT_H * coeffs[0];
        for (int i = 1; i < 32; i++) {
            total += coeffs[i] * Math.cos(i * (Math.PI / 32D) * (t + 0.5D));
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
        upsampled = upsampleControlPoints(frame.getFlowHelper());
        float renderDistance = 1.0f;
        arcs = computeIntermediarySamples(renderDistance);
        float arcLength = (arcs.length - 2f) * renderDistance + arcs[arcs.length - 1].arcLength;
        if (arcLength <= 0D)
            return;
        TaskList<Void> tasks = new TaskList<>(frame.getFlowHelper().getThreadPool());
        for (int i = 0; i < arcs.length; i++) {
            tasks.submit(i, (j) -> {
                SplineArc arc = arcs[j];
                float progressAlongArc = Math.min(1.0f, j * renderDistance / arcLength);
                float t = 31f * progressAlongArc;
                float[] values = new float[3];
                values[0] = fourierICT(coeffX, t) * arc.arcLength;
                values[1] = fourierICT(coeffY, t) * arc.arcLength;
                values[2] = fourierICT(coeffB, t) * arc.arcLength;
                float sigma = fourierICT(coeffSigma, t);
                float inverseSigma = 1f / sigma;
                float maxColor = MathHelper.max(0.01f, values[0], values[1], values[2]);
                float maxDist = (float)Math.sqrt(-2f * sigma * sigma * ((float)Math.log(0.1D) * 3f - maxColor));
                int xBegin = Math.max(0, MathHelper.round(arc.location.x - maxDist));
                int xEnd = Math.min(frame.getFrameHeader().width - 1, MathHelper.round(arc.location.x + maxDist));
                int yBegin = Math.max(0, MathHelper.round(arc.location.y - maxDist));
                int yEnd = Math.min(frame.getFrameHeader().height - 1, MathHelper.round(arc.location.y + maxDist));
                for (int c = 0; c < 3; c++) {
                    for (int y = yBegin; y <= yEnd; y++) {
                        float[] buffer = frame.getBuffer()[c][y];
                        for (int x = xBegin; x <= xEnd; x++) {        
                            float diffX = x - arc.location.x;
                            float diffY = y - arc.location.y;
                            float distance = (float)Math.sqrt(diffX * diffX + diffY * diffY);
                            float factor = MathHelper.erf((0.5f * distance + (float)MathHelper.SQRT_F) * inverseSigma);
                            factor -= MathHelper.erf((0.5f * distance - (float)MathHelper.SQRT_F) * inverseSigma);
                            float extra = 0.25f * values[c] * sigma * factor * factor;
                            synchronized(buffer) {
                                buffer[x] += extra;
                            }
                        }
                    }
                }
            });
        }
        tasks.collect();
    }
}
