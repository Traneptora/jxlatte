package com.thebombzen.jxlatte.bundle;

import java.io.IOException;
import java.util.Objects;

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

    @Override
    public int hashCode() {
        return Objects.hash(tps_numerator, tps_denominator, num_loops, have_timecodes);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AnimationHeader other = (AnimationHeader) obj;
        return tps_numerator == other.tps_numerator && tps_denominator == other.tps_denominator
                && num_loops == other.num_loops && have_timecodes == other.have_timecodes;
    }

    @Override
    public String toString() {
        return "AnimationHeader [tps_numerator=" + tps_numerator + ", tps_denominator=" + tps_denominator
                + ", num_loops=" + num_loops + ", have_timecodes=" + have_timecodes + "]";
    }
}
