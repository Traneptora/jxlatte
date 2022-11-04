package com.thebombzen.jxlatte.entropy;

import java.util.OptionalInt;

public class ANSState {
    private OptionalInt state = OptionalInt.empty();

    public ANSState() {}

    public boolean hasState() {
        return state.isPresent();
    }

    public int getState() {
        return state.orElseThrow(() -> new IllegalStateException("ANS state has not been initialized"));
    }

    public void setState(int state) {
        this.state = OptionalInt.of(state);
    }

    public void reset() {
        state = OptionalInt.empty();
    }
}
