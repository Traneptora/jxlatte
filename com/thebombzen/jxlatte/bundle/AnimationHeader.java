package com.thebombzen.jxlatte.bundle;

import java.io.IOException;

import com.thebombzen.jxlatte.io.Bitreader;

public class AnimationHeader {

    public final int tps_numerator;
    public final int tps_denominator;
    public final int num_loops;
    public final boolean have_timecodes;

    public AnimationHeader(Bitreader reader) throws IOException {
        tps_numerator = reader.readU32(100, 0, 1000, 0, 1, 10, 1, 30);
        tps_denominator = reader.readU32(1, 0, 1001, 0, 1, 8, 1, 10);
        num_loops = reader.readU32(0, 0, 0, 3, 0, 16, 0, 32);
        have_timecodes = reader.readBool();
    }
}
