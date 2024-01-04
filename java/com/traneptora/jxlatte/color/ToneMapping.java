package com.thebombzen.jxlatte.color;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.io.Bitreader;

public class ToneMapping {
    public final float intensityTarget;
    public final float minNits;
    public final boolean relativeToMaxDisplay;
    public final float linearBelow;

    public ToneMapping() {
        intensityTarget = 255f;
        minNits = 0f;
        relativeToMaxDisplay = false;
        linearBelow = 0f;
    }

    public ToneMapping(Bitreader reader) throws IOException {
        if (reader.readBool()) {
            intensityTarget = 255f;
            minNits = 0f;
            relativeToMaxDisplay = false;
            linearBelow = 0f;
        } else {
            intensityTarget = reader.readF16();
            if (intensityTarget <= 0f)
                throw new InvalidBitstreamException("Intensity Target must be positive");
            minNits = reader.readF16();
            if (minNits <= 0f)
                throw new InvalidBitstreamException("Min Nits must be positive");
            if (minNits > intensityTarget)
                throw new InvalidBitstreamException("Min Nits must be at most the Intensity Target");
            relativeToMaxDisplay = reader.readBool();
            linearBelow = reader.readF16();
            if (relativeToMaxDisplay && (linearBelow < 0f || linearBelow > 1f))
                throw new InvalidBitstreamException("Linear Below out of relative range");
            if (!relativeToMaxDisplay && linearBelow < 0f)
                throw new InvalidBitstreamException("Linear Below must be nonnegative");
        }
    }
}
