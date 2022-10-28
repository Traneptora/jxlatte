package com.thebombzen.jxlatte.frame.modular;

public class MADecisionNode implements MANode {
    public final int property;
    public final int value;
    public final int leftChildIndex;
    public final int rightChildIndex;
    public MADecisionNode(int property, int value, int leftChildIndex, int rightChildIndex) {
        this.property = property;
        this.value = value;
        this.leftChildIndex = leftChildIndex;
        this.rightChildIndex = rightChildIndex;
    }
}
