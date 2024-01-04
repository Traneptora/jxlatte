package com.traneptora.jxlatte.util;

import java.util.Collections;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ChebyschevApproximation implements DoubleUnaryOperator {
    private double[] c;
    private double ab;
    private double s;

    public ChebyschevApproximation(double a, double b, int n, DoubleUnaryOperator func) {
        ab = a + b;
        s = 1.0D / (b - a); 

        double bma = 0.5 * (b - a);
        double bpa = 0.5 * ab;
        double[] f = IntStream.range(0, n).mapToDouble(k -> Math.cos(Math.PI * (k + 0.5D) / n) * bma + bpa).map(func).toArray();
        double fac = 2.0D / n;
        List<Double> l = IntStream.range(0, n).mapToDouble(j -> {
            return fac * IntStream.range(0, n).mapToDouble(k -> f[k] * Math.cos(Math.PI * j * (k + 0.5D) / n)).sum();
        }).boxed().collect(Collectors.toList());
        Collections.reverse(l);
        c = l.stream().mapToDouble(Double::doubleValue).toArray();
    }

    @Override
    public double applyAsDouble(double x) {
        double y = (2.0D * x - ab) * s;
        double y2 = 2.0D * y;
        double d = c[0];
        double dPrev = 0D;
        for (int j = 1; j < c.length - 1; j++) {
            double d2 = y2 * d - dPrev + c[j];
            dPrev = d;
            d = d2;
        }
        return y * d - dPrev + 0.5 * c[c.length - 1];
    }
}
