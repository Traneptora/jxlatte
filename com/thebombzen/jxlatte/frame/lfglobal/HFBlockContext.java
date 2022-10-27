package com.thebombzen.jxlatte.frame.lfglobal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.thebombzen.jxlatte.MathHelper;
import com.thebombzen.jxlatte.io.Bitreader;

public class HFBlockContext {
    private int[] clusterMap;
    public HFBlockContext(Bitreader reader) throws IOException {
        boolean useDefault = reader.readBool();
        if (useDefault) {
            clusterMap = new int[]{0, 1, 2, 2, 3, 3, 4, 5, 6, 6, 6, 6, 6,
                7, 8, 9, 9, 10, 11, 12, 13, 14, 14, 14, 14, 14,
                7, 8, 9, 9, 10, 11, 12, 13, 14, 14, 14, 14, 14};
            return;
        }
        int[] nbLFThresh = new int[3];
        List<Integer>[] lfThresholds = new List[3];
        for (int i = 0; i < 3; i++) {
            lfThresholds[i] = new ArrayList<Integer>();
            nbLFThresh[i] = reader.readBits(4);
            for (int j = 0; j < nbLFThresh[i]; j++) {
                int t = MathHelper.unpackSigned(reader.readU32(0, 4, 16, 8, 272, 16, 65808, 32));
                lfThresholds[i].add(Integer.valueOf(t));
            }
            // TODO finish
        }
    }
}
