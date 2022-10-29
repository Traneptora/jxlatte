package com.thebombzen.jxlatte.entropy;

import java.io.IOException;
import java.util.Objects;

import com.thebombzen.jxlatte.io.Bitreader;

public abstract class SymbolDistribution {
    protected HybridIntegerConfig config;
    protected int logBucketSize;
    protected int alphabetSize;
    protected int logAlphabetSize;

    public abstract int readSymbol(Bitreader reader) throws IOException;

    @Override
    public int hashCode() {
        return Objects.hash(config, logBucketSize, alphabetSize, logAlphabetSize);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SymbolDistribution other = (SymbolDistribution) obj;
        return Objects.equals(config, other.config) && logBucketSize == other.logBucketSize
                && alphabetSize == other.alphabetSize && logAlphabetSize == other.logAlphabetSize;
    }
}
