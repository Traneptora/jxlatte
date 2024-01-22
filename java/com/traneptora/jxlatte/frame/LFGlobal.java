package com.traneptora.jxlatte.frame;

import java.io.IOException;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.frame.features.Patch;
import com.traneptora.jxlatte.frame.features.noise.NoiseParameters;
import com.traneptora.jxlatte.frame.features.spline.SplinesBundle;
import com.traneptora.jxlatte.frame.modular.GlobalModular;
import com.traneptora.jxlatte.frame.vardct.HFBlockContext;
import com.traneptora.jxlatte.frame.vardct.LFChannelCorrelation;
import com.traneptora.jxlatte.frame.vardct.Quantizer;
import com.traneptora.jxlatte.io.Bitreader;

public class LFGlobal {
    public final Frame frame;
    public final Patch[] patches;
    public final SplinesBundle splines;
    public final NoiseParameters noiseParameters;
    public final float[] lfDequant = new float[]{1f / 4096f, 1f / 512f, 1f / 256f};
    public final Quantizer quantizer;
    public final HFBlockContext hfBlockCtx;
    public final LFChannelCorrelation lfChanCorr;
    public final GlobalModular gModular;

    public LFGlobal(Bitreader reader, Frame parent) throws IOException {
        this.frame = parent;
        if ((frame.getFrameHeader().flags & FrameFlags.PATCHES) != 0) {
            EntropyStream stream = new EntropyStream(frame.getLoggers(), reader, 10);
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
            splines = new SplinesBundle(frame.getLoggers(), reader);
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
                lfDequant[i] = reader.readF16() * (1f / 128f);
            }
        }
        if (frame.getFrameHeader().encoding == FrameFlags.VARDCT) {
            quantizer = new Quantizer(reader, lfDequant);
            hfBlockCtx = new HFBlockContext(reader, parent.getLoggers());
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
