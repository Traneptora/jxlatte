package com.traneptora.jxlatte.frame.features.noise;

public class XorShiro {

    public static long splitMix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private long[] state0 = new long[8];
    private long[] state1 = new long[8];
    private long[] batch = new long[8];
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

    public long nextLong() {
        fillBatch();
        return batch[batchPos++];
    }

    public void fill(int[] bits) {
        for (int i = 0; i < bits.length; i += 2) {
            long l = nextLong();
            bits[i] = (int)(l & 0xFF_FF_FF_FFL);
            bits[i + 1] = (int)((l >>> 32) & 0xFF_FF_FF_FFL);
        }
    }

    private void fillBatch() {
        if (batchPos < batch.length)
            return;
        for (int i = 0; i < batch.length; i++) {
            final long a = state1[i];
            long b = state0[i];
            batch[i] = a + b;
            state0[i] = a;
            b ^= b << 23;
            state1[i] = b ^ a ^ (b >>> 18) ^ (a >>> 5);
        }
        batchPos = 0;
    }
}
