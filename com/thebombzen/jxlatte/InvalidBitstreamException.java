package com.thebombzen.jxlatte;

public class InvalidBitstreamException extends Exception {
    public InvalidBitstreamException(String s) {
        super(s);
    }

    public InvalidBitstreamException(String s, Throwable t) {
        super(s, t);
    }

    public InvalidBitstreamException(Throwable t) {
        super(t);
    }

    public InvalidBitstreamException() {
        super();
    }
}
