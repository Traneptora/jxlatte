package com.traneptora.jxlatte.entropy;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;

import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.InvalidBitstreamException;
import com.traneptora.jxlatte.util.MathHelper;

public class PrefixSymbolDistribution extends SymbolDistribution {

    private static final VLCTable level0Table = new VLCTable(4, Stream.of(new int[][]{
        {0, 2}, {4, 2}, {3, 2}, {2, 3}, {0, 2}, {4, 2}, {3, 2}, {1, 4},
        {0, 2}, {4, 2}, {3, 2}, {2, 3}, {0, 2}, {4, 2}, {3, 2}, {5, 4}
    }).map(a -> new VLCTableEntry(a[0], a[1])).toArray(VLCTableEntry[]::new));

    private static final int[] codelenMap = {1, 2, 3, 4, 0, 5, 17, 6, 16, 7, 8, 9, 10, 11, 12, 13, 14, 15};

    private VLCTable table;
    private int defaultSymbol;

    private void populateSimplePrefix(Bitreader reader) throws IOException {
        int[] symbols = new int[4];
        int[] lens = null;
        int nsym = 1 + reader.readBits(2);
        boolean treeSelect = false;
        int bits = 0;
        for (int i = 0; i < nsym; i++)
            symbols[i] = reader.readBits(logAlphabetSize);
        if (nsym == 4)
            treeSelect = reader.readBool();
        switch (nsym) {
            case 1:
                table = null;
                defaultSymbol = symbols[0];
                return;
            case 2:
                bits = 1;
                lens = new int[]{1, 1, 0, 0};
                if (symbols[0] > symbols[1]) {
                    int temp = symbols[1];
                    symbols[1] = symbols[0];
                    symbols[0] = temp;
                }
                break;
            case 3:
                bits = 2;
                lens = new int[]{1, 2, 2, 0};
                if (symbols[1] > symbols[2]) {
                    int temp = symbols[2];
                    symbols[2] = symbols[1];
                    symbols[1] = temp;
                }
                break;
            case 4:
                if (treeSelect) {
                    bits = 3;
                    lens = new int[]{1, 2, 3, 3};
                    if (symbols[2] > symbols[3]) {
                        int temp = symbols[3];
                        symbols[3] = symbols[2];
                        symbols[2] = temp;
                    }
                } else {
                    bits = 2;
                    lens = new int[]{2, 2, 2, 2};
                    Arrays.sort(symbols);
                }
                break;
        }
        this.table = new VLCTable(bits, lens, symbols);
    }

    private void populateComplexPrefix(Bitreader reader, int hskip) throws IOException {
        int[] level1Lengths = new int[18];
        int[] level1Codecounts = new int[19];

        level1Codecounts[0] = hskip;

        int totalCode = 0;
        int numCodes = 0;
        for (int i = hskip; i < 18; i++) {
            int code = level1Lengths[codelenMap[i]] = level0Table.getVLC(reader);
            level1Codecounts[code]++;
            if (code != 0) {
                totalCode += (32 >>> code);
                numCodes++;
            }
            if (totalCode >= 32) {
                level1Codecounts[0] += 17 - i;
                break;
            }
        }

        if (totalCode != 32 && numCodes >= 2 || numCodes < 1)
            throw new InvalidBitstreamException("Invalid Level 1 Prefix codes");

        for (int i = 1; i < 19; i++)
            level1Codecounts[i] += level1Codecounts[i - 1];

        int[] level1LengthsScrambled = new int[18];
        int[] level1Symbols = new int[18];
        for (int i = 17; i >= 0; i--) {
            int index = --level1Codecounts[level1Lengths[i]];
            level1LengthsScrambled[index] = level1Lengths[i];
            level1Symbols[index] = i;
        }

        VLCTable leve11Table;

        if (numCodes == 1) {
            leve11Table = new VLCTable(0, new VLCTableEntry[]{new VLCTableEntry(level1Symbols[17], 0)});
        } else {
            leve11Table = new VLCTable(5, level1LengthsScrambled, level1Symbols);
        }

        totalCode = 0;
        int prevRepeatCount = 0;
        int prevZeroCount = 0;

        int[] level2Lengths = new int[alphabetSize];
        int[] level2Symbols = new int[alphabetSize];
        int[] level2Counts = new int[alphabetSize + 1];

        int prev = 8;
        for (int i = 0; i < alphabetSize; i++) {
            int code = leve11Table.getVLC(reader);
            if (code == 16) {
                int extra = 3 + reader.readBits(2);
                if (prevRepeatCount > 0)
                    extra = 4 * (prevRepeatCount - 2) - prevRepeatCount + extra;
                for (int j = 0; j < extra; j++)
                    level2Lengths[i + j] = prev;
                totalCode += (32768 >>> prev) * extra;
                i += extra - 1;
                prevRepeatCount += extra;
                prevZeroCount = 0;
                level2Counts[prev] += extra;
            } else if (code == 17) {
                int extra = 3 + reader.readBits(3);
                if (prevZeroCount > 0)
                    extra = 8 * (prevZeroCount - 2) - prevZeroCount + extra;
                i += extra - 1;
                prevRepeatCount = 0;
                prevZeroCount += extra;
                level2Counts[0] += extra;
            } else {
                level2Lengths[i] = code;
                prevRepeatCount = 0;
                prevZeroCount = 0;
                if (code != 0) {
                    totalCode += 32768 >>> code;
                    prev = code;
                }
                level2Counts[code]++;
            }
            if (totalCode >= 32768) {
                level2Counts[0] += alphabetSize - i - 1;
                break;
            }
        }

        if (totalCode != 32768 && level2Counts[0] < alphabetSize - 1)
            throw new InvalidBitstreamException("Invalid Level 2 Prefix Codes");

        for (int i = 1; i <= alphabetSize; i++)
            level2Counts[i] += level2Counts[i - 1];

        int[] level2LengthsScrambled = new int[alphabetSize];
        for (int i = alphabetSize - 1; i >= 0; i--) {
            int index = --level2Counts[level2Lengths[i]];
            level2LengthsScrambled[index] = level2Lengths[i];
            level2Symbols[index] = i;
        }

        this.table = new VLCTable(15, level2LengthsScrambled, level2Symbols);
    }

    public PrefixSymbolDistribution(Bitreader reader, int alphabetSize) throws IOException {
        this.alphabetSize = alphabetSize;
        this.logAlphabetSize = MathHelper.ceilLog1p(alphabetSize - 1);

        if (alphabetSize == 1) {
            table = null;
            defaultSymbol = 0;
            return;
        }

        int hskip = reader.readBits(2);
        if (hskip == 1)
            populateSimplePrefix(reader);
        else
            populateComplexPrefix(reader, hskip);       
    }

    @Override
    public int readSymbol(Bitreader reader, EntropyState state) throws IOException {
        if (table == null)
            return defaultSymbol;
        return table.getVLC(reader);
    }
}
