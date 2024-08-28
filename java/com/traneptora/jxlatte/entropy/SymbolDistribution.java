package com.traneptora.jxlatte.entropy;

import java.io.IOException;

import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.MutableLong;

public abstract class SymbolDistribution {
    protected HybridIntegerConfig config;
    protected int logBucketSize;
    protected int alphabetSize;
    protected int logAlphabetSize;

    public abstract int readSymbol(Bitreader reader, MutableLong state) throws IOException;
}
