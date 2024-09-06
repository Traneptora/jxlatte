package com.traneptora.jxlatte.entropy;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.io.Bitreader;

public class VLCTable {
    private int[][] table;
    private int bits;

    public VLCTable(int bits, int[][] table) {
        this.bits = bits;
        this.table = table;
    }

    public VLCTable(int bits, int[] lengths, int[] symbols) throws InvalidBitstreamException {
        this.bits = bits;
        int[][] table = new int[1 << bits][2];
        int[] codes = new int[lengths.length];
        int[] nLengths = new int[lengths.length];
        int[] nSymbols = new int[lengths.length];
        int count = 0;
        long code = 0;
        for (int i = 0; i < lengths.length; i++) {
            int len = lengths[i];
            if (len > 0) {
                nLengths[count] = len;
                nSymbols[count] = symbols != null ? symbols[i] : i;
                codes[count] = (int)code;
                count++;
            } else if (len < 0) {
                len = -len;
            } else {
                continue;
            }
            code += 1L << (32 - len);
            if (code > (1L << 32))
                throw new IllegalArgumentException("Too many VLC codes");
        }
        if (code != (1L << 32))
            throw new IllegalArgumentException("Not enough VLC codes");
        for (int i = 0; i < count; i++) {
            if (nLengths[i] <= bits) {
                int index = Integer.reverse(codes[i]);
                int number = 1 << (bits - nLengths[i]);
                int offset = 1 << nLengths[i];
                for (int j = 0; j < number; j++) {
                    int oldSymbol = table[index][0];
                    int oldLen = table[index][1];
                    if ((oldLen > 0 || oldSymbol > 0) && (oldLen != nLengths[i] || oldSymbol != nSymbols[i]))
                        throw new InvalidBitstreamException("Illegal VLC codes");
                    table[index][0] = nSymbols[i];
                    table[index][1] = nLengths[i];
                    index += offset;
                }
            } else {
                throw new InvalidBitstreamException("Table size too small");
            }
        }
        for (int i = 0; i < table.length; i++) {
            if (table[i][1] == 0)
                table[i][0] = -1;
        }
        this.table = table;
    }

    public int getVLC(Bitreader reader) throws IOException {
        int index = reader.showBits(bits);
        int symbol = table[index][0];
        int length = table[index][1];
        reader.skipBits(length);
        return symbol;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.deepHashCode(table);
        result = prime * result + Objects.hash(bits);
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
        VLCTable other = (VLCTable) obj;
        return Arrays.deepEquals(table, other.table) && bits == other.bits;
    }
}
