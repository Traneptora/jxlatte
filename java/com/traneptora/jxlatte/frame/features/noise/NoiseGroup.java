package com.traneptora.jxlatte.frame.features.noise;

import com.traneptora.jxlatte.frame.FrameHeader;

public class NoiseGroup {

    private XorShiro rng;

    public NoiseGroup(FrameHeader header, long seed0, float[][][] noiseBuffer, int y0, int x0) {
        long seed1 = (((long)x0 & 0xFF_FF_FF_FFL) << 32) | ((long)y0 & 0xFF_FF_FF_FFL);
        int ySize = Math.min(header.groupDim, header.bounds.size.height - y0);
        int xSize = Math.min(header.groupDim, header.bounds.size.width - x0);
        rng = new XorShiro(seed0, seed1);
        int[] bits = new int[16];
        for (int c = 0; c < noiseBuffer.length; c++) {
            for (int y = 0; y < ySize; y++) {
                for (int x = 0; x < xSize; x += 16) {
                    rng.fill(bits);
                    for (int i = 0; i < 16 && x + i < xSize; i++) {
                        int f = (bits[i] >>> 9) | 0x3f_80_00_00;
                        noiseBuffer[c][y0 + y][x0 + x + i] = Float.intBitsToFloat(f);
                    }
                }
            }
        }
    }
}
