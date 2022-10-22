package com.thebombzen.jxlatte;

import java.io.IOException;

public class InvalidBitstreamException extends IOException {
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
