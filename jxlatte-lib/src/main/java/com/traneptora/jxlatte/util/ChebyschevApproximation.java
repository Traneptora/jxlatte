package com.traneptora.jxlatte.util;

import com.traneptora.jxlatte.util.functional.FloatUnaryOperator;

public class ChebyschevApproximation implements FloatUnaryOperator {
    private final float[] c;
    private final float ab;
    private final float s;

    public ChebyschevApproximation(float a, float b, int n, FloatUnaryOperator func) {
        ab = a + b;
        s = 1.0f / (b - a); 

        float bma = 0.5f * (b - a);
        float bpa = 0.5f * ab;
        float[] f = new float[n];
        for (int k = 0; k < n; k++)
            f[k] = func.applyAsFloat((float)Math.cos(Math.PI * (k + 0.5D) / n) * bma + bpa);
        float fac = 2.0f / n;
        c = new float[n];
        for (int j = 0; j < n; j++) {
            float tot = 0.0f;
            for (int k = 0; k < n; k++)
                tot += f[k] * (float)Math.cos(Math.PI * j * (k + 0.5D) / n);
            c[n - 1 - j] = tot * fac;
        }
    }

    @Override
    public float applyAsFloat(float x) {
        float y = (2.0f * x - ab) * s;
        float y2 = 2.0f * y;
        float d = c[0];
        float dPrev = 0f;
        for (int j = 1; j < c.length - 1; j++) {
            float d2 = y2 * d - dPrev + c[j];
            dPrev = d;
            d = d2;
        }
        return y * d - dPrev + 0.5f * c[c.length - 1];
    }
}
