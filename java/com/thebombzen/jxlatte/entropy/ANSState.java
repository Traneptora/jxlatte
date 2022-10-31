package com.thebombzen.jxlatte.entropy;

public class ANSState {
    private boolean initialized;
    private int state;
    private int initialState;

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
        if (!initialized)
            this.initialState = state;
        this.state = state;
        this.initialized = true;
    }

    public void reset() {
        this.initialized = false;
    }

    public int getInitialState() {
        if (!initialized)
            throw new IllegalStateException("state has not been initialized");
        return this.initialState;
    }
}
