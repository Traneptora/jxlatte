package com.thebombzen.jxlatte.bundle;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

public class FrameHeader {
    public FrameHeader(Bitreader reader, ImageHeader parent) throws IOException {
        boolean allDefault = reader.readBool();
        
    }
}
