package com.thebombzen.jxlatte.entropy;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.io.Bitreader;

public class DistributionBundle {
    private boolean usesLZ77;
    private int lz77MinSymbol;
    private int lz77MinLength;
    private HybridUintConfig lzLengthConfig;
    private int[] clusterMap;
    private SymbolDistribution[] dists;
    private int logAlphabetSize;
    private int numToCopy77;
    private int copyPos77;
    private int numDecoded77;
    private int[] window;
    private ANSState state = new ANSState();

    public int readSymbol(Bitreader reader, int context) throws IOException {
        if (numToCopy77 > 0) {
            int hybridUint = window[copyPos77++ & 0xFFFFF];
            numToCopy77--;
            window[numDecoded77++ & 0xFFFFF] = hybridUint;
            return hybridUint;
        }

        if (context >= clusterMap.length)
            throw new IllegalArgumentException("Context cannot be bigger than bundle length");
        if (clusterMap[context] >= dists.length)
            throw new InvalidBitstreamException("Cluster Map points to nonexisted distribution");
        
        SymbolDistribution dist = dists[clusterMap[context]];
        int token = dist.readSymbol(reader);

        if (usesLZ77 && token >= lz77MinSymbol) {
            SymbolDistribution lz77dist = dists[clusterMap[clusterMap.length - 1]];
            numToCopy77 = lz77MinLength + readHybridUint(reader, lzLengthConfig, token - lz77MinSymbol);
            token = lz77dist.readSymbol(reader);
            int distance = 1 + readHybridUint(reader, lz77dist.config, token);
            if (distance > (1 << 20))
                distance = 1 << 20;
            if (distance > numDecoded77)
                distance = numDecoded77;
            copyPos77 = numDecoded77 - distance;
            return readSymbol(reader, context);
        }

        int hybridUint = readHybridUint(reader, dist.config, token);
        if (usesLZ77)
            window[numDecoded77++ & 0xFFFFF] = hybridUint;
        return hybridUint;
    }

    private int readHybridUint(Bitreader reader, HybridUintConfig config, int token) throws IOException {
        int split = 1 << config.splitExponent;
        if (token < split)
            return token;
        int n = config.splitExponent - config.lsbInToken - config.msbInToken
            + ((token - split) >>> (config.msbInToken + config.lsbInToken));
        if (n >= 32)
            throw new InvalidBitstreamException("n is too large");
        int low = token & ((1 << config.lsbInToken) - 1);
        token >>>= config.lsbInToken;
        token &= (1 << config.msbInToken) - 1;
        token |= 1 << config.msbInToken;
        return (((token << n) | reader.readBits(n)) << config.lsbInToken) | low;
    }

    private void readClustering(Bitreader reader, int numDists) throws IOException {

        clusterMap = new int[numDists];

        if (numDists == 1) {
            clusterMap[0] = 0;
        } else if (reader.readBool()) {
            // simple clustering
            int nbits = reader.readBits(2);
            for (int i = 0; i < numDists; i++)
                clusterMap[i] = reader.readBits(nbits);
        } else {
            boolean useMtf = reader.readBool();
            DistributionBundle nested = new DistributionBundle(reader, 1);
            for (int i = 0; i < numDists; i++)
                clusterMap[i] = nested.readSymbol(reader, 0);
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

        if (numClusters > numDists)
            throw new InvalidBitstreamException("Can't have more clusters than dists");

        dists = new SymbolDistribution[numClusters];
        
    }

    public DistributionBundle(Bitreader reader, int numDists) throws IOException {
        if (numDists <= 0)
            throw new IllegalArgumentException("Num Dists must be positive");
        
        usesLZ77 = reader.readBool();
        if (usesLZ77) {
            lz77MinSymbol = reader.readU32(224, 0, 512, 0, 4096, 0, 8, 15);
            lz77MinLength = reader.readU32(3, 0, 4, 0, 5, 2, 9, 8);
            numDists++;
            lzLengthConfig = new HybridUintConfig(reader, 8);
            window = new int[1 << 20];
        }

        readClustering(reader, numDists);

        boolean prefixCodes = reader.readBool();

        logAlphabetSize = prefixCodes ? 15 : 5 + reader.readBits(2);
        
        HybridUintConfig[] configs = new HybridUintConfig[dists.length];

        for (int i = 0; i < configs.length; i++)
            configs[i] = new HybridUintConfig(reader, logAlphabetSize);
        
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
                dists[i] = new ANSSymbolDistribution(reader, state, logAlphabetSize);
        }
        for (int i = 0; i < dists.length; i++)
            dists[i].config = configs[i];
    }
}
