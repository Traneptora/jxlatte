package com.thebombzen.jxlatte.frame.lfglobal;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.MathHelper;
import com.thebombzen.jxlatte.entropy.EntropyStream;
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
        numClusters = EntropyStream.readClusterMap(reader, clusterMap, 16);
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(clusterMap);
        result = prime * result + Arrays.deepHashCode(lfThresholds);
        result = prime * result + Arrays.hashCode(qfThresholds);
        result = prime * result + Objects.hash(numClusters);
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        HFBlockContext other = (HFBlockContext) obj;
        return Arrays.equals(clusterMap, other.clusterMap) && numClusters == other.numClusters
                && Arrays.deepEquals(lfThresholds, other.lfThresholds)
                && Arrays.equals(qfThresholds, other.qfThresholds);
    }
    @Override
    public String toString() {
        return "HFBlockContext [clusterMap=" + Arrays.toString(clusterMap) + ", numClusters=" + numClusters
                + ", lfThresholds=" + Arrays.toString(lfThresholds) + ", qfThresholds=" + Arrays.toString(qfThresholds)
                + "]";
    }
    
}
