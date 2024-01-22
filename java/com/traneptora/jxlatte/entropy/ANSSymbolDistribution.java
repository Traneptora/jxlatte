package com.traneptora.jxlatte.entropy;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.io.Bitreader;

public class ANSSymbolDistribution extends SymbolDistribution {

    private static final VLCTable distPrefixTable = new VLCTable(7, new int[][]{
        {10, 3}, {12, 7}, {7, 3}, {3, 4}, {6, 3}, {8, 3}, {9, 3}, {5, 4},
        {10, 3}, {4, 4},  {7, 3}, {1, 4}, {6, 3}, {8, 3}, {9, 3}, {2, 4},
        {10, 3}, {0, 5},  {7, 3}, {3, 4}, {6, 3}, {8, 3}, {9, 3}, {5, 4},
        {10, 3}, {4, 4},  {7, 3}, {1, 4}, {6, 3}, {8, 3}, {9, 3}, {2, 4},
        {10, 3}, {11, 6}, {7, 3}, {3, 4}, {6, 3}, {8, 3}, {9, 3}, {5, 4},
        {10, 3}, {4, 4},  {7, 3}, {1, 4}, {6, 3}, {8, 3}, {9, 3}, {2, 4},
        {10, 3}, {0, 5},  {7, 3}, {3, 4}, {6, 3}, {8, 3}, {9, 3}, {5, 4},
        {10, 3}, {4, 4},  {7, 3}, {1, 4}, {6, 3}, {8, 3}, {9, 3}, {2, 4},
        {10, 3}, {13, 7}, {7, 3}, {3, 4}, {6, 3}, {8, 3}, {9, 3}, {5, 4},
        {10, 3}, {4, 4},  {7, 3}, {1, 4}, {6, 3}, {8, 3}, {9, 3}, {2, 4},
        {10, 3}, {0, 5},  {7, 3}, {3, 4}, {6, 3}, {8, 3}, {9, 3}, {5, 4},
        {10, 3}, {4, 4},  {7, 3}, {1, 4}, {6, 3}, {8, 3}, {9, 3}, {2, 4},
        {10, 3}, {11, 6}, {7, 3}, {3, 4}, {6, 3}, {8, 3}, {9, 3}, {5, 4},
        {10, 3}, {4, 4},  {7, 3}, {1, 4}, {6, 3}, {8, 3}, {9, 3}, {2, 4},
        {10, 3}, {0, 5},  {7, 3}, {3, 4}, {6, 3}, {8, 3}, {9, 3}, {5, 4},
        {10, 3}, {4, 4},  {7, 3}, {1, 4}, {6, 3}, {8, 3}, {9, 3}, {2, 4}
    });

    private int[] frequencies;
    private int[] cutoffs;
    private int[] symbols;
    private int[] offsets;

    public ANSSymbolDistribution(Bitreader reader, int logAlphabetSize) throws IOException {
        this.logAlphabetSize = logAlphabetSize;
        int uniqPos = -1;
        if (reader.readBool()) {
            // simple distribution
            if (reader.readBool()) {
                int v1 = reader.readU8();
                int v2 = reader.readU8();
                if (v1 == v2)
                    throw new InvalidBitstreamException("Overlapping dual peak distribution");
                this.alphabetSize = 1 + Math.max(v1, v2);
                if (this.alphabetSize > (1 << logAlphabetSize))
                    throw new InvalidBitstreamException("Illegal Alphabet Size: " + this.alphabetSize);
                this.frequencies = new int[alphabetSize];
                frequencies[v1] = reader.readBits(12);
                frequencies[v2] = (1 << 12) - frequencies[v1];
                if (frequencies[v1] == 0)
                    uniqPos = v2;
            } else {
                int x = reader.readU8();
                this.alphabetSize = 1 + x;
                this.frequencies = new int[alphabetSize];
                frequencies[x] = 1 << 12;
                uniqPos = x;
            }
        } else if (reader.readBool()) {
            // flat distribution
            alphabetSize = 1 + reader.readU8();
            if (this.alphabetSize > (1 << logAlphabetSize))
                throw new InvalidBitstreamException("Illegal Alphabet Size: " + this.alphabetSize);
            if (alphabetSize == 1)
                uniqPos = 0;
            frequencies = new int[alphabetSize];
            for (int i = 0; i < alphabetSize; i++)
                frequencies[i] = (1 << 12) / alphabetSize;
            for (int i = 0; i < (1 << 12) % alphabetSize; i++)
                frequencies[i]++;
        } else {
            int len;
            for (len = 0; len < 3; len++) {
                if (!reader.readBool())
                    break;
            }
            int shift = (reader.readBits(len) | (1 << len)) - 1;
            if (shift > 13)
                throw new InvalidBitstreamException("Shift > 13");
            this.alphabetSize = 3 + reader.readU8();
            if (this.alphabetSize > (1 << logAlphabetSize))
                throw new InvalidBitstreamException("Illegal Alphabet Size: " + this.alphabetSize);
            this.frequencies = new int[alphabetSize];
            int[] logCounts = new int[alphabetSize];
            int[] same = new int[alphabetSize];
            int omitLog = -1;
            int omitPos = -1;
            for (int i = 0; i < alphabetSize; i++) {
                logCounts[i] = distPrefixTable.getVLC(reader);
                if (logCounts[i] == 13) {
                    int rle = reader.readU8();
                    same[i] = rle + 5;
                    i += rle + 3;
                    continue;
                }
                if (logCounts[i] > omitLog) {
                    omitLog = logCounts[i];
                    omitPos = i;
                }
            }

            if (omitPos < 0 || omitPos + 1 < alphabetSize && logCounts[omitPos + 1] == 13)
                throw new InvalidBitstreamException("Invalid OmitPos");
            
            int totalCount = 0, numSame = 0, prev = 0;
            for (int i = 0; i < alphabetSize; i++ ) {
                if (same[i] != 0) {
                    numSame = same[i] - 1;
                    prev = i > 0 ? frequencies[i - 1] : 0;
                }
                if (numSame != 0) {
                    frequencies[i] = prev;
                    numSame--;
                } else {
                    if (i == omitPos || logCounts[i] == 0)
                        continue;
                    if (logCounts[i] == 1) {
                        frequencies[i] = 1;
                    } else {
                        int bitcount = shift - ((12 - logCounts[i] + 1) >> 1);
                        if (bitcount < 0)
                            bitcount = 0;
                        if (bitcount > logCounts[i] - 1)
                            bitcount = logCounts[i] - 1;
                        frequencies[i] = (1 << (logCounts[i] - 1)) + (reader.readBits(bitcount) << (logCounts[i] - 1 - bitcount));
                    }
                }
                totalCount += frequencies[i];
            }
            frequencies[omitPos] = (1 << 12) - totalCount;
        }
        generateAliasMapping(uniqPos);
    }

    @Override
    public int readSymbol(Bitreader reader, ANSState stateObj) throws IOException {
        int state =  stateObj.hasState() ? stateObj.getState() : reader.readBits(32);
        int index = state & 0xFFF;
        int i = index >>> logBucketSize;
        int pos = index & ((1 << logBucketSize) - 1);
        boolean greater = pos >= cutoffs[i];
        int symbol = greater ? symbols[i] : i;
        int offset = greater ? offsets[i] + pos : pos;
        state = frequencies[symbol] * (state >>> 12) + offset;
        if ((state & 0xFFFF0000) == 0)
            state = (state << 16) | reader.readBits(16);
        stateObj.setState(state);

        return symbol;
    }

    private void generateAliasMapping(int uniqPos) {

        logBucketSize = 12 - logAlphabetSize;
        Deque<Integer> overfull = new ArrayDeque<>();
        Deque<Integer> underfull = new ArrayDeque<>();
        int bucketSize = 1 << logBucketSize;
        int tableSize = 1 << logAlphabetSize;

        symbols = new int[tableSize];
        cutoffs = new int[tableSize];
        offsets = new int[tableSize];

        if (uniqPos >= 0) {
            for (int i = 0; i < tableSize; i++) {
                symbols[i] = uniqPos;
                offsets[i] = i * bucketSize;
                cutoffs[i] = 0;
            }
            return;
        }

        for (int i = 0; i < alphabetSize; i++) {
            cutoffs[i] = frequencies[i];
            if (cutoffs[i] > bucketSize)
                overfull.addFirst(i);
            else if (cutoffs[i] < bucketSize)
                underfull.addFirst(i);
        }

        for (int i = alphabetSize; i < tableSize; i++)
            underfull.addFirst(i);

        while (!overfull.isEmpty()) {
            int u = underfull.removeFirst();
            int o = overfull.removeFirst();
            int by = bucketSize - cutoffs[u];
            cutoffs[o] -= by;
            symbols[u] = o;
            offsets[u] = cutoffs[o];
            if (cutoffs[o] < bucketSize)
                underfull.addFirst(o);
            else if (cutoffs[o] > bucketSize)
                overfull.addFirst(o);
        }

        for (int i = 0; i < tableSize; i++) {
            if (cutoffs[i] == bucketSize) {
                symbols[i] = i;
                offsets[i] = 0;
                cutoffs[i] = 0;
            } else {
                offsets[i] -= cutoffs[i];
            }
        }
    }
}
