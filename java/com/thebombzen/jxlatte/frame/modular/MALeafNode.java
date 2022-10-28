package com.thebombzen.jxlatte.frame.modular;

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
}
