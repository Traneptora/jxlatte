package com.thebombzen.jxlatte.entropy;

import java.util.Objects;

public class ANSState {
    private boolean initialized;
    private int state;

    public ANSState() {
        this.initialized = false;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public int getState() {
        if (!initialized)
            throw new IllegalStateException("state has not been initialized");
        return state;
    }

    public void setState(int state) {
        this.state = state;
        this.initialized = true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(initialized, state);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ANSState other = (ANSState) obj;
        return initialized == other.initialized && state == other.state;
    }

    @Override
    public String toString() {
        return "ANSState [initialized=" + initialized + ", state=" + state + "]";
    }
}
