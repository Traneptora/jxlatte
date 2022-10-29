package com.thebombzen.jxlatte.frame.modular;

import java.util.Objects;

public class MALeafNode implements MANode {
    public final int context;
    public final int predictor;
    public final int offset;
    public final int multiplier;
    public MALeafNode(int context, int predictor, int offset, int multiplier) {
        this.context = context;
        this.predictor = predictor;
        this.offset = offset;
        this.multiplier = multiplier;
    }
    @Override
    public int hashCode() {
        return Objects.hash(context, predictor, offset, multiplier);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MALeafNode other = (MALeafNode) obj;
        return context == other.context && predictor == other.predictor && offset == other.offset
                && multiplier == other.multiplier;
    }
    @Override
    public String toString() {
        return "MALeafNode [context=" + context + ", predictor=" + predictor + ", offset=" + offset + ", multiplier="
                + multiplier + "]";
    }
}
