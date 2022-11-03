package com.thebombzen.jxlatte.entropy;

import static com.thebombzen.jxlatte.util.FunctionalHelper.constant;

import java.util.OptionalInt;

public class ANSState {
    private OptionalInt state = OptionalInt.empty();

    public ANSState() {}

    public boolean hasState() {
        return state.isPresent();
    }

    public int getState() {
        return state.orElseThrow(constant(IllegalStateException::new, "ANS state has not been initialized"));
    }

    public void setState(int state) {
        this.state = OptionalInt.of(state);
    }

    public void reset() {
        state = OptionalInt.empty();
    }
}
