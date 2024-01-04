package com.thebombzen.jxlatte.util;

import java.util.Iterator;

public class RangeIterator implements Iterator<Integer>, Iterable<Integer> {
    private final int end;
    private int current;

    public RangeIterator(int start, int end) {
        this.current = start;
        this.end = end;
    }

    public boolean hasNext() {
        return current < end;
    }

    public Integer next() {
        return current++;
    }

    public Iterator<Integer> iterator() {
        return this;
    }
}
