package com.traneptora.jxlatte.frame.features;

import java.util.Random;

public class XorShiro extends Random {

    private static final long serialVersionUID = 0xe9f7cb31bea47c85L;

    public static long splitMix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private long[] state0 = new long[8];
    private long[] state1 = new long[8];
    private int[] batch = new int[16];
    private int batchPos = batch.length;

    public XorShiro(int seed0, int seed1, int seed2, int seed3) {
        this(((long)seed0 << 32) | ((long)seed1 & 0xFF_FF_FF_FFL),
            ((long)seed2 << 32) | ((long)seed3 & 0xFF_FF_FF_FFL));
    }

    public XorShiro(long seed0, long seed1) {
        state0[0] = splitMix64(seed0 + 0x9e3779b97f4a7c15L);
        state1[0] = splitMix64(seed1 + 0x9e3779b97f4a7c15L);
        for (int i = 1; i < 8; i++) {
            state0[i] = splitMix64(state0[i - 1]);
            state1[i] = splitMix64(state1[i - 1]);
        }
    }

    public void fill(int[] bits) {
        for (int i = 0; i < bits.length; i++) {
            if (batchPos >= batch.length)
                fillBatch();
            bits[i] = batch[batchPos++];
        }
    }

    @Override
    public int next(int bits) {
        if (batchPos >= batch.length)
            fillBatch();
        return batch[batchPos++] >>> (32 - bits);
    }

    private void fillBatch() {
        for (int i = 0; i < 8; i++) {
            final long a = state1[i];
            long b = state0[i];
            final long c = a + b;
            state0[i] = a;
            b ^= b << 23;
            state1[i] = b ^ a ^ (b >>> 18) ^ (a >>> 5);
            batch[2 * i] = (int)(c & 0xFF_FF_FF_FFL);
            batch[2 * i + 1] = (int)(c >>> 32);
        }
        batchPos = 0;
    }
}
