package com.traneptora.jxlatte.util;

import java.util.Iterator;

public class Range2DIterator implements Iterable<IntPoint>, Iterator<IntPoint> {
    private final int startX;
    private int currY;
    private int currX;
    private final int endX;
    private final int endY;
    
    public Range2DIterator(int startX, int startY, int endX, int endY) {
        this.startX = startX;
        this.currX = startY;
        this.currX = startX;
        this.endX = endX;
        this.endY = endY;
    }

    public boolean hasNext() {
        return currY < endY;
    }

    public IntPoint next() {
        IntPoint ret = new IntPoint(currX++, currY);
        if (currX >= endX) {
            currY++;
            currX = startX;
        }
        return ret;
    }

    public Iterator<IntPoint> iterator() {
        return this;
    }
}
