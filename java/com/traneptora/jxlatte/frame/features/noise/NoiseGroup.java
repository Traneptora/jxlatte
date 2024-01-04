package com.traneptora.jxlatte.frame.features.noise;

import com.traneptora.jxlatte.frame.FrameHeader;

public class NoiseGroup {

    private XorShiro rng;

    public NoiseGroup(FrameHeader header, long seed0, float[][][] noiseBuffer, int x0, int y0) {
        long seed1 = ((long)x0 << 32) | (long)y0;      
        int xSize = Math.min(header.groupDim, header.width - x0);
        int ySize = Math.min(header.groupDim, header.height - y0);
        rng = new XorShiro(seed0, seed1);
        int[] bits = new int[16];
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < ySize; y++) {
                for (int x = 0; x < xSize; x += 16) {
                    rng.fill(bits);
                    for (int i = 0; i < 16 && x + i < xSize; i++) {
                        int f = (bits[i] >>> 9) | 0x3f_80_00_00;
                        noiseBuffer[c][y0 + y][x0 + x + i] = Float.intBitsToFloat(f);
                    }
                }
                // LIBJXL: libjxl bug generates an extra unused batch here if xSize % 16 == 0
            }
        }
    }
}
