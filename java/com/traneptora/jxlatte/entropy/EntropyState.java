package com.traneptora.jxlatte.entropy;

import java.util.Objects;

public class EntropyState {
    private int state;
    private boolean hasState;

    public EntropyState() {
        hasState = false;
    }

    public boolean hasState() {
        return hasState;
    }

    public int getState() {
        if (hasState)
            return state;
        throw new IllegalStateException("ANS state has not been initialized");
    }

    public void setState(int state) {
        this.state = state;
        hasState = true;
    }

    public void reset() {
        hasState = false;
    }

    @Override
    public String toString() {
        if (!hasState) {
            return "EntropyState [hasState=false]";
        } else {
            return String.format("EntropyState [hasState=true, state=%d]", state);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, hasState);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EntropyState other = (EntropyState) obj;
        return state == other.state && hasState == other.hasState;
    }
}
