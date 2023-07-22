package com.thebombzen.jxlatte.frame.group;

import java.io.IOException;
import java.util.Arrays;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.io.Bitreader;

public class PassesInfo {
    public final int numPasses;
    public final int numDS;
    public final int[] shift;
    public final int[] downSample;
    public final int[] lastPass;

    public PassesInfo() {
        numPasses = 1;
        numDS = 0;
        shift = new int[0];
        downSample = new int[0];
        lastPass = new int[0];
    }

    public PassesInfo(Bitreader reader) throws IOException {
        numPasses = reader.readU32(1, 0, 2, 0, 3, 0, 4, 3);
        numDS = numPasses != 1 ? reader.readU32(0, 0, 1, 0, 2, 0, 3, 1) : 0;
        if (numDS >= numPasses)
            throw new InvalidBitstreamException("num_ds < num_passes violated");
        shift = new int[numPasses - 1];
        for (int i = 0; i < shift.length; i++)
            shift[i] = reader.readBits(2);
        downSample = new int[numDS + 1];
        for (int i = 0; i < numDS; i++)
            downSample[i] = 1 << reader.readBits(2);
        lastPass = new int[numDS + 1];
        for (int i = 0; i < numDS; i++)
            lastPass[i] = reader.readU32(0, 0, 1, 0, 2, 0, 0, 3);
        downSample[numDS] = 1;
        lastPass[numDS] = numPasses - 1;
    }

    @Override
    public String toString() {
        return String.format("PassesInfo [numPasses=%s, numDS=%s, shift=%s, downSample=%s, lastPass=%s]", numPasses,
                numDS, Arrays.toString(shift), Arrays.toString(downSample), Arrays.toString(lastPass));
    }
}
