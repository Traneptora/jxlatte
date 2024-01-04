package com.thebombzen.jxlatte.entropy;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

public abstract class SymbolDistribution {
    protected HybridIntegerConfig config;
    protected int logBucketSize;
    protected int alphabetSize;
    protected int logAlphabetSize;

    public abstract int readSymbol(Bitreader reader, ANSState state) throws IOException;
}
