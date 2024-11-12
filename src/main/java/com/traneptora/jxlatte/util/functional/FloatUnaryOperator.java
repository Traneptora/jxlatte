package com.traneptora.jxlatte.util.functional;

@FunctionalInterface
public interface FloatUnaryOperator {
    public float applyAsFloat(float x);

    public default FloatUnaryOperator compose(FloatUnaryOperator g) {
        return new FloatUnaryOperator() {
            public float applyAsFloat(float x) {
                return FloatUnaryOperator.this.applyAsFloat(g.applyAsFloat(x));
            }
        };
    }
}
