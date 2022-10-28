package com.thebombzen.jxlatte.frame.lfglobal;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.MathHelper;
import com.thebombzen.jxlatte.entropy.EntropyDecoder;
import com.thebombzen.jxlatte.io.Bitreader;

public class HFBlockContext {
    public final int[] clusterMap;
    public final int numClusters;
    public final int[][] lfThresholds = new int[3][];
    public final int[] qfThresholds;
    public HFBlockContext(Bitreader reader) throws IOException {
        boolean useDefault = reader.readBool();
        if (useDefault) {
            clusterMap = new int[]{0, 1, 2, 2, 3, 3, 4, 5, 6, 6, 6, 6, 6,
                7, 8, 9, 9, 10, 11, 12, 13, 14, 14, 14, 14, 14,
                7, 8, 9, 9, 10, 11, 12, 13, 14, 14, 14, 14, 14};
            numClusters = 15;
            qfThresholds = new int[0];
            lfThresholds[0] = lfThresholds[1] = lfThresholds[2] = new int[0];
            return;
        }
        int[] nbLFThresh = new int[3];
        for (int i = 0; i < 3; i++) {
            nbLFThresh[i] = reader.readBits(4);
            lfThresholds[i] = new int[nbLFThresh[i]];
            for (int j = 0; j < nbLFThresh[i]; j++) {
                int t = MathHelper.unpackSigned(reader.readU32(0, 4, 16, 8, 272, 16, 65808, 32));
                lfThresholds[i][j] = t;
            }
        }
        int nbQfThresh = reader.readBits(4);
        qfThresholds = new int[nbQfThresh];
        for (int i = 0; i < nbQfThresh; i++) {
            qfThresholds[i] = reader.readU32(0, 2, 4, 3, 12, 5, 44, 8);
        }
        int bSize = 39 * (nbQfThresh + 1);
        for (int i = 0; i < 3; i++) {
            bSize *= nbLFThresh[i] + 1;
        }
        if (bSize > 39 * 64) {
            throw new InvalidBitstreamException("bSize too large");
        }
        clusterMap = new int[bSize];
        numClusters = EntropyDecoder.readClusterMap(reader, clusterMap, 16);
    }
}
