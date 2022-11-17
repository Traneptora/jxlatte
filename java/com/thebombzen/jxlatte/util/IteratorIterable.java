package com.thebombzen.jxlatte.util;

import java.util.Iterator;
import java.util.stream.IntStream;

public class IteratorIterable<T> implements Iterator<T>, Iterable<T> {

    private Iterator<T> iterator;

    public IteratorIterable(Iterator<T> iterator) {
        this.iterator = iterator;
    }

    public IteratorIterable(Iterable<T> iterable) {
        this.iterator = iterable.iterator();
    }
    
    @Override
    public Iterator<T> iterator() {
        return iterator;
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public T next() {
        return iterator.next();
    }

    public static IteratorIterable<Integer> range(int startIndex, int endIndex) {
        return new IteratorIterable<>(IntStream.range(startIndex, endIndex).iterator());
    }
}
