package com.thebombzen.jxlatte.frame;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.frame.features.Patch;
import com.thebombzen.jxlatte.frame.features.noise.NoiseParameters;
import com.thebombzen.jxlatte.frame.features.spline.SplinesBundle;
import com.thebombzen.jxlatte.frame.modular.GlobalModular;
import com.thebombzen.jxlatte.frame.vardct.HFBlockContext;
import com.thebombzen.jxlatte.frame.vardct.LFChannelCorrelation;
import com.thebombzen.jxlatte.frame.vardct.Quantizer;
import com.thebombzen.jxlatte.io.Bitreader;

public class LFGlobal {
    public final Frame frame;
    public final Patch[] patches;
    public final SplinesBundle splines;
    public final NoiseParameters noiseParameters;
    public final double[] lfDequant = new double[]{1D / 4096D, 1D / 512D, 1D / 256D};
    public final Quantizer quantizer;
    public final HFBlockContext hfBlockCtx;
    public final LFChannelCorrelation lfChanCorr;
    public final GlobalModular gModular;

    public LFGlobal(Bitreader reader, Frame parent) throws IOException {
        this.frame = parent;
        if ((frame.getFrameHeader().flags & FrameFlags.PATCHES) != 0) {
            EntropyStream stream = new EntropyStream(reader, 10);
            int numPatches = stream.readSymbol(reader, 0);
            patches = new Patch[numPatches];
            for (int i = 0; i < numPatches; i++) {
                patches[i] = new Patch(stream, reader,
                    parent.globalMetadata.getExtraChannelCount(), parent.globalMetadata.getNumAlphaChannels());
            }
            if (!stream.validateFinalState())
                throw new InvalidBitstreamException("Illegal ANS final state");
        } else {
            patches = new Patch[0];
        }
        if ((frame.getFrameHeader().flags & FrameFlags.SPLINES) != 0) {
            if (frame.globalMetadata.getColorChannelCount() < 3)
                throw new InvalidBitstreamException("Cannot do splines in grayscale");
            splines = new SplinesBundle(reader);
        } else {
            splines = null;
        }
        if ((frame.getFrameHeader().flags & FrameFlags.NOISE) != 0) {
            if (frame.globalMetadata.getColorChannelCount() < 3)
                throw new InvalidBitstreamException("Cannot do noise in grayscale");
            noiseParameters = new NoiseParameters(reader);
        } else {
            noiseParameters = null;
        }
        if (!reader.readBool()) {
            for (int i = 0; i < 3; i++) {
                lfDequant[i] = reader.readF16() * (1D / 128D);
            }
        }
        if (frame.getFrameHeader().encoding == FrameFlags.VARDCT) {
            quantizer = new Quantizer(reader, lfDequant);
            hfBlockCtx = new HFBlockContext(reader);
            lfChanCorr = new LFChannelCorrelation(reader);
        } else {
            quantizer = null;
            hfBlockCtx = null;
            // default value used for splines and noise in modular mode
            lfChanCorr = new LFChannelCorrelation();
        }
        gModular = new GlobalModular(reader, frame);
    }
}
