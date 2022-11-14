package com.thebombzen.jxlatte.frame.features.spline;

import java.util.ArrayList;
import java.util.List;

import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.LFGlobal;
import com.thebombzen.jxlatte.util.IntPoint;
import com.thebombzen.jxlatte.util.MathHelper;
import com.thebombzen.jxlatte.util.TaskList;

public class Spline {
    public final IntPoint[] controlPoints;
    private DoublePoint[] upsampled;
    private SplineArc[] arcs;
    private double[] coeffX;
    private double[] coeffY;
    private double[] coeffB;
    private double[] coeffSigma;
    private int splineID;

    public Spline(int splineID, IntPoint[] controlPoints) {
        this.controlPoints = controlPoints;
    }

    private DoublePoint[] upsampleControlPoints() {
        if (controlPoints.length == 1) {
            return new DoublePoint[]{new DoublePoint(controlPoints[0])};
        }
        IntPoint[] extended = new IntPoint[controlPoints.length + 2];
        extended[0] = controlPoints[0].plus(controlPoints[0]).minus(controlPoints[1]);
        for (int i = 0; i < controlPoints.length; i++) {
            extended[i + 1] = controlPoints[i];
        }
        extended[extended.length - 1] = controlPoints[controlPoints.length - 1].plus(controlPoints[controlPoints.length - 1]).minus(controlPoints[controlPoints.length - 2]);
        DoublePoint[] upsampled = new DoublePoint[16 * (extended.length - 3) + 1];
        TaskList<Void> tasks = new TaskList<>();
        for (int i = 0; i < extended.length - 3; i++) {
            final int j = i;
            tasks.submit(() -> {
                double[] t = new double[4];
                DoublePoint[] p = new DoublePoint[4];
                DoublePoint[] a = new DoublePoint[3];
                DoublePoint[] b = new DoublePoint[3];
                for (int k = 0; k < 4; k++)
                    p[k] = new DoublePoint(extended[j + k]);
                upsampled[j * 16] = p[1];
                t[0] = 0D;
                for (int k = 1; k < 4; k++)
                    t[k] = t[k - 1] + Math.pow(p[k].minus(p[k - 1]).normSquared(), 0.25D);
                for (int step = 1; step < 16; step++) {
                    double knot = t[1] + 0.0625D * step * (t[2] - t[1]);
                    for (int k = 0; k < 3; k++) {
                        double f = (knot - t[k]) / (t[k + 1] - t[k]);
                        a[k] = p[k + 1].minus(p[k]).times(f).plus(p[k]);
                    }
                    for (int k = 0; k < 2; k++) {
                        double f = (knot - t[k]) / (t[k + 2] - t[k]);
                        b[k] = a[k + 1].minus(a[k]).times(f).plus(a[k]);
                    }
                    double f = (knot - t[1]) / (t[2] - t[1]);
                    upsampled[j * 16 + step] = b[1].minus(b[0]).times(f).plus(b[0]);
                }
            });
        }
        upsampled[upsampled.length - 1] = new DoublePoint(controlPoints[controlPoints.length - 1]);
        tasks.collect();
        return upsampled;
    }

    private SplineArc[] computeIntermediarySamples(double renderDistance) {
        DoublePoint current = upsampled[0];
        int nextID = 0;
        List<SplineArc> allSamples = new ArrayList<>();
        allSamples.add(new SplineArc(current, renderDistance));
        while (nextID < upsampled.length) {
            DoublePoint prev = new DoublePoint(current);
            double arcLengthFromPrevious = 0D;
            while (true) {
                if (nextID >= upsampled.length) {
                    allSamples.add(new SplineArc(prev, arcLengthFromPrevious));
                    break;
                }
                DoublePoint next = upsampled[nextID];
                double arcLengthToNext = next.minus(prev).norm();
                if (arcLengthFromPrevious + arcLengthToNext >= renderDistance) {
                    double f = (renderDistance - arcLengthFromPrevious) / arcLengthToNext;
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

    private static double fourierICT(double[] coeffs, double t) {
        double total = MathHelper.SQRT_H * coeffs[0];
        for (int i = 1; i < 32; i++) {
            total += coeffs[i] * Math.cos(i * (Math.PI / 32D) * (t + 0.5D));
        }
        return total;
    }

    private void computeCoeffs(int splineID, LFGlobal lfGlobal) {
        coeffX = new double[32];
        coeffY = new double[32];
        coeffB = new double[32];
        coeffSigma = new double[32];
        double quantAdjust = lfGlobal.splines.quantAdjust / 8D;
        double invQa = quantAdjust >= 0 ? 1D / (1D + quantAdjust) : 1.0D - quantAdjust;
        double yAdjust = 0.106066017D * invQa;
        double xAdjust = 0.005939697D * invQa;
        double bAdjust = 0.098994949D * invQa;
        double sigmaAdjust = 0.47135738D * invQa;
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
        upsampled = upsampleControlPoints();
        double renderDistance = 1.0D;
        arcs = computeIntermediarySamples(renderDistance);
        double arcLength = (arcs.length - 2D) * renderDistance + arcs[arcs.length - 1].arcLength;
        if (arcLength <= 0D)
            return;
        TaskList<Void> tasks = new TaskList<>();
        for (int i = 0; i < arcs.length; i++) {
            tasks.submit(i, (j) -> {
                SplineArc arc = arcs[j];
                double progressAlongArc = Math.min(1.0D, j * renderDistance / arcLength);
                double t = 31D * progressAlongArc;
                double[] values = new double[3];
                values[0] = fourierICT(coeffX, t) * arc.arcLength;
                values[1] = fourierICT(coeffY, t) * arc.arcLength;
                values[2] = fourierICT(coeffB, t) * arc.arcLength;
                double sigma = fourierICT(coeffSigma, t);
                double inverseSigma = 1D / sigma;
                double maxColor = MathHelper.max(0.01D, values[0], values[1], values[2]);
                double maxDist = Math.sqrt(-2D * sigma * sigma * (Math.log(0.1D) * 3D - maxColor));
                int xBegin = Math.max(0, MathHelper.round(arc.location.x - maxDist));
                int xEnd = Math.min(frame.getFrameHeader().width - 1, MathHelper.round(arc.location.x + maxDist));
                int yBegin = Math.max(0, MathHelper.round(arc.location.y - maxDist));
                int yEnd = Math.min(frame.getFrameHeader().height - 1, MathHelper.round(arc.location.y + maxDist));
                for (int c = 0; c < 3; c++) {
                    for (int y = yBegin; y <= yEnd; y++) {
                        double[] buffer = frame.getBuffer()[c][y];
                        for (int x = xBegin; x <= xEnd; x++) {        
                            double diffX = x - arc.location.x;
                            double diffY = y - arc.location.y;
                            double distance = Math.sqrt(diffX * diffX + diffY * diffY);
                            double factor = MathHelper.erf((0.5D * distance + MathHelper.SQRT_F) * inverseSigma);
                            factor -= MathHelper.erf((0.5D * distance - MathHelper.SQRT_F) * inverseSigma);
                            double extra = 0.25D * values[c] * sigma * factor * factor;
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
