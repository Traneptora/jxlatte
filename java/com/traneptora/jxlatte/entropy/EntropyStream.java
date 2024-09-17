package com.traneptora.jxlatte.entropy;

import java.io.IOException;

import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.InvalidBitstreamException;
import com.traneptora.jxlatte.io.Loggers;

public class EntropyStream {

    private static final int[][] SPECIAL_DISTANCES = {
        {0, 1}, {1, 0}, {1, 1}, {-1, 1}, {0, 2}, {2, 0}, {1, 2}, {-1, 2}, {2, 1}, {-2, 1}, {2, 2},
        {-2, 2}, {0, 3}, {3, 0}, {1, 3}, {-1, 3}, {3, 1}, {-3, 1}, {2, 3}, {-2, 3}, {3, 2},
        {-3, 2}, {0, 4}, {4, 0}, {1, 4}, {-1, 4}, {4, 1}, {-4, 1}, {3, 3}, {-3, 3}, {2, 4},
        {-2, 4}, {4, 2}, {-4, 2}, {0, 5}, {3, 4}, {-3, 4}, {4, 3}, {-4, 3}, {5, 0}, {1, 5},
        {-1, 5}, {5, 1}, {-5, 1}, {2, 5}, {-2, 5}, {5, 2}, {-5, 2}, {4, 4}, {-4, 4}, {3, 5},
        {-3, 5}, {5, 3}, {-5, 3}, {0, 6}, {6, 0}, {1, 6}, {-1, 6}, {6, 1}, {-6, 1}, {2, 6},
        {-2, 6}, {6, 2}, {-6, 2}, {4, 5}, {-4, 5}, {5, 4}, {-5, 4}, {3, 6}, {-3, 6}, {6, 3},
        {-6, 3}, {0, 7}, {7, 0}, {1, 7}, {-1, 7}, {5, 5}, {-5, 5}, {7, 1}, {-7, 1}, {4, 6},
        {-4, 6}, {6, 4}, {-6, 4}, {2, 7}, {-2, 7}, {7, 2}, {-7, 2}, {3, 7}, {-3, 7}, {7, 3},
        {-7, 3}, {5, 6}, {-5, 6}, {6, 5}, {-6, 5}, {8, 0}, {4, 7}, {-4, 7}, {7, 4}, {-7, 4},
        {8, 1}, {8, 2}, {6, 6}, {-6, 6}, {8, 3}, {5, 7}, {-5, 7}, {7, 5}, {-7, 5}, {8, 4}, {6, 7},
        {-6, 7}, {7, 6}, {-7, 6}, {8, 5}, {7, 7}, {-7, 7}, {8, 6}, {8, 7},
    };

    private boolean usesLZ77;
    private int lz77MinSymbol;
    private int lz77MinLength;
    private HybridIntegerConfig lzLengthConfig;
    private int[] clusterMap;
    private SymbolDistribution[] dists;
    private int logAlphabetSize;
    private int numToCopy77;
    private int copyPos77;
    private int numDecoded77;
    private int[] window;
    private EntropyState ansState = new EntropyState();
    private Loggers loggers;

    /**  
     * returns the number of clusters
     */
    public static int readClusterMap(Loggers loggers, Bitreader reader, int[] clusterMap, int maxClusters) throws IOException {
        int numDists = clusterMap.length;
        if (numDists == 1) {
            clusterMap[0] = 0;
        } else if (reader.readBool()) {
            // simple clustering
            int nbits = reader.readBits(2);
            for (int i = 0; i < numDists; i++)
                clusterMap[i] = reader.readBits(nbits);
        } else {
            boolean useMtf = reader.readBool();
            EntropyStream nested = new EntropyStream(loggers, reader, 1, numDists <= 2);
            for (int i = 0; i < numDists; i++)
                clusterMap[i] = nested.readSymbol(reader, 0);
            if (!nested.validateFinalState())
                throw new InvalidBitstreamException("Nested distribution");
            if (useMtf) {
                int[] mtf = new int[256];
                for (int i = 0; i < 256; i++)
                    mtf[i] = i;
                for (int i = 0; i < numDists; i++) {
                    int index = clusterMap[i];
                    clusterMap[i] = mtf[index];
                    if (index != 0) {
                        int value = mtf[index];
                        for (int j = index; j > 0; j--)
                            mtf[j] = mtf[j - 1];
                        mtf[0] = value;
                    }
                }
            }
        }

        int numClusters = 0;
        for (int i = 0; i < numDists; i++) {
            if (clusterMap[i] >= numClusters)
                numClusters = clusterMap[i] + 1;
        }
        if (numClusters > maxClusters)
            throw new InvalidBitstreamException("Too many clusters");
        
        return numClusters;
    }

    public EntropyStream(EntropyStream stream) {
        this.usesLZ77 = stream.usesLZ77;
        this.lz77MinLength = stream.lz77MinLength;
        this.lz77MinSymbol = stream.lz77MinSymbol;
        this.lzLengthConfig = stream.lzLengthConfig;
        this.clusterMap = stream.clusterMap;
        this.logAlphabetSize = stream.logAlphabetSize;
        this.dists = stream.dists;
        if (this.usesLZ77)
            window = new int[1 << 20];
        this.loggers = stream.loggers;
    }

    public EntropyStream(Loggers loggers, Bitreader reader, int numDists) throws IOException {
        this(loggers, reader, numDists, false);
    }

    private EntropyStream(Loggers loggers, Bitreader reader, int numDists, boolean disallowLZ77) throws IOException {
        if (numDists <= 0)
            throw new IllegalArgumentException("Num Dists must be positive");

        this.loggers = loggers;

        usesLZ77 = reader.readBool();
        if (usesLZ77) {
            if (disallowLZ77)
                throw new InvalidBitstreamException("Nested distributions cannot use LZ77");
            lz77MinSymbol = reader.readU32(224, 0, 512, 0, 4096, 0, 8, 15);
            lz77MinLength = reader.readU32(3, 0, 4, 0, 5, 2, 9, 8);
            numDists++;
            lzLengthConfig = new HybridIntegerConfig(reader, 8);
            window = new int[1 << 20];
        }

        clusterMap = new int[numDists];
        int numClusters = readClusterMap(loggers, reader, clusterMap, numDists);
        dists = new SymbolDistribution[numClusters];

        loggers.log(Loggers.LOG_TRACE, "clusters: %d", numClusters);

        boolean prefixCodes = reader.readBool();
        logAlphabetSize = prefixCodes ? 15 : 5 + reader.readBits(2);
        HybridIntegerConfig[] configs = new HybridIntegerConfig[dists.length];

        for (int i = 0; i < configs.length; i++)
            configs[i] = new HybridIntegerConfig(reader, logAlphabetSize);

        loggers.log(Loggers.LOG_TRACE, "Configs: %s", (Object)configs);

        if (prefixCodes) {
            int[] alphabetSizes = new int[dists.length];
            for (int i = 0; i < dists.length; i++) {
                if (reader.readBool()) {
                    int n = reader.readBits(4);
                    alphabetSizes[i] = 1 + (1 << n) + reader.readBits(n);
                } else {
                    alphabetSizes[i] = 1;
                }
            }
            for (int i = 0; i < dists.length; i++)
                dists[i] = new PrefixSymbolDistribution(reader, alphabetSizes[i]);
        } else {
            for (int i = 0; i < dists.length; i++)
                dists[i] = new ANSSymbolDistribution(reader, logAlphabetSize);
        }
        for (int i = 0; i < dists.length; i++)
            dists[i].config = configs[i];
    }

    public void reset() {
        this.numToCopy77 = 0;
        ansState.reset();
    }

    public boolean validateFinalState() {
        return !ansState.hasState() || ansState.getState() == 0x130000;
    }

    public int readSymbol(Bitreader reader, int context) throws IOException {
        return readSymbol(reader, context, 0);
    }

    public int readSymbol(Bitreader reader, int context, int distanceMultiplier) throws IOException {
        if (numToCopy77 > 0) {
            int hybridInt = window[copyPos77++ & 0xFFFFF];
            numToCopy77--;
            window[numDecoded77++ & 0xFFFFF] = hybridInt;
            return hybridInt;
        }

        if (context >= clusterMap.length)
            throw new IllegalArgumentException("Context cannot be bigger than bundle length");
        if (clusterMap[context] >= dists.length)
            throw new InvalidBitstreamException("Cluster Map points to nonexisted distribution");

        SymbolDistribution dist = dists[clusterMap[context]];
        int token = dist.readSymbol(reader, ansState);

        if (usesLZ77 && token >= lz77MinSymbol) {
            SymbolDistribution lz77dist = dists[clusterMap[clusterMap.length - 1]];
            numToCopy77 = lz77MinLength + readHybridInteger(reader, lzLengthConfig, token - lz77MinSymbol);
            token = lz77dist.readSymbol(reader, ansState);
            int distance = readHybridInteger(reader, lz77dist.config, token);
            if (distanceMultiplier == 0) {
                distance++;
            } else if (distance < 120) {
                distance = SPECIAL_DISTANCES[distance][0] + distanceMultiplier * SPECIAL_DISTANCES[distance][1];
            } else {
                distance -= 119;
            }
            if (distance > (1 << 20))
                distance = 1 << 20;
            if (distance > numDecoded77)
                distance = numDecoded77;
            copyPos77 = numDecoded77 - distance;
            return readSymbol(reader, context);
        }

        int hybridInt = readHybridInteger(reader, dist.config, token);
        if (usesLZ77)
            window[numDecoded77++ & 0xFFFFF] = hybridInt;

        return hybridInt;
    }

    private int readHybridInteger(Bitreader reader, HybridIntegerConfig config, int token) throws IOException {
        int split = 1 << config.splitExponent;
        if (token < split)
            return token;
        int n = config.splitExponent - config.lsbInToken - config.msbInToken
            + ((token - split) >>> (config.msbInToken + config.lsbInToken));
        if (n > 32)
            throw new InvalidBitstreamException("n is too large");
        int low = token & ((1 << config.lsbInToken) - 1);
        token >>>= config.lsbInToken;
        token &= (1 << config.msbInToken) - 1;
        token |= 1 << config.msbInToken;
        return (((token << n) | reader.readBits(n)) << config.lsbInToken) | low;
    }
}
