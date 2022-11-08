package com.thebombzen.jxlatte.util;

public class Point {
    public int x;
    public int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Point(Point p) {
        this(p.x, p.y);
    }

    public Point() {
        this(0, 0);
    }

    public Point plus(Point p) {
        return new Point(x + p.x, y + p.y);
    }

    public Point minus(Point p) {
        return new Point(x - p.x, y - p.y);
    }

    public Point times(double factor) {
        return new Point((int)(x * factor), (int)(y * factor));
    }

    public void plusEquals(Point p) {
        this.x += p.x;
        this.y += p.y;
    }

    public void minusEquals(Point p) {
        this.x -= p.x;
        this.y -= p.y;
    }

    public void timesEquals(double factor) {
        this.x = (int)(this.x * factor);
        this.y = (int)(this.y * factor);
    }
}

