package com.thebombzen.jxlatte.frame.modular;

import java.util.Objects;

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
    @Override
    public int hashCode() {
        return Objects.hash(property, value, leftChildIndex, rightChildIndex);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MADecisionNode other = (MADecisionNode) obj;
        return property == other.property && value == other.value && leftChildIndex == other.leftChildIndex
                && rightChildIndex == other.rightChildIndex;
    }
    @Override
    public String toString() {
        return "MADecisionNode [property=" + property + ", value=" + value + ", leftChildIndex=" + leftChildIndex
                + ", rightChildIndex=" + rightChildIndex + "]";
    }
}
