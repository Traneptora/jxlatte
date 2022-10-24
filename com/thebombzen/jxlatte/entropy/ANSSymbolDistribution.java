package com.thebombzen.jxlatte.entropy;

import java.io.IOException;

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
    private int uniqPos;
    private ANSState state;

    public ANSSymbolDistribution(Bitreader reader, ANSState state, int logAlphabetSize) throws IOException {
        this.state = state;
        // TODO initialize dist
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
