package com.thebombzen.jxlatte.entropy;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.io.Bitreader;

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
    private int uniqPos = -1;
    private ANSState state;

    public ANSSymbolDistribution(Bitreader reader, ANSState state, int logAlphabetSize) throws IOException {
        this.state = state;
        this.logAlphabetSize = logAlphabetSize;
        if (reader.readBool()) {
            // simple distribution
            this.alphabetSize = 256;
            this.frequencies = new int[alphabetSize];
            if (reader.readBool()) {
                int v1 = reader.readU8();
                int v2 = reader.readU8();
                if (v1 == v2)
                    throw new InvalidBitstreamException("Overlapping dual peak distribution");
                frequencies[v1] = reader.readBits(12);
                frequencies[v2] = (1 << 12) - frequencies[v1];
                if (frequencies[v1] == 0)
                    uniqPos = v2;
            } else {
                int x = reader.readU8();
                frequencies[x] = 1 << 12;
                uniqPos = x;
            }
        } else if (reader.readBool()) {
            // flat distribution
            this.alphabetSize = 1 + reader.readU8();
            this.frequencies = new int[alphabetSize];
            for (int i = 0; i < alphabetSize; i++)
                frequencies[i] = (1 << 12) / alphabetSize;
            for (int i = 0; i < (1 << 12) % alphabetSize; i++)
                frequencies[i]++;
        } else {
            int len = 0;
            do {
                if (!reader.readBool())
                    break;
            } while (++len < 3);
            int shift = (reader.readBits(len) | (1 << len)) - 1;
            if (shift > 13)
                throw new InvalidBitstreamException("Shift > 13");
            this.alphabetSize = 3 + reader.readU8();
            this.frequencies = new int[alphabetSize];
        }
    }

    @Override
    public int readSymbol(Bitreader reader) throws IOException {
        if (!this.state.isInitialized())
            this.state.setState(reader.readBits(32));

        int state = this.state.getState();
        int index = state & 0xFFF;
        int i = index >>> logBucketSize;
        int pos = index & ((1 << logBucketSize) - 1);
        int symbol = pos >= cutoffs[i] ? symbols[i] : i;
        int offset = pos >= cutoffs[i] ? offsets[i] + pos : pos;
        state = frequencies[symbol] * (state >>> 12) + offset;
        if (state < (1 << 16))
            state = (state << 16) | reader.readBits(16);
        this.state.setState(state);

        return symbol;
    }

    public void generateAliasMapping() throws IOException {
        // TODO gen alias map
    }
}
