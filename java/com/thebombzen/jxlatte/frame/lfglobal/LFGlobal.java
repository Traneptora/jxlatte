package com.thebombzen.jxlatte.frame.lfglobal;

import java.io.IOException;

import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.io.Bitreader;

public class LFGlobal {
    public final Frame frame;
    public final Patch[] patches;
    public final Splines splines;
    public final NoiseParamters noiseParamters;
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
            throw new UnsupportedOperationException("Patches not yet implemented");
        } else {
            patches = new Patch[0];
        }
        if ((frame.getFrameHeader().flags & FrameFlags.SPLINES) != 0) {
            throw new UnsupportedOperationException("Splines");
        } else {
            splines = null;
        }
        if ((frame.getFrameHeader().flags & FrameFlags.NOISE) != 0) {
            throw new UnsupportedOperationException("Noise");
        } else {
            noiseParamters = null;
        }
        if (!reader.readBool()) {
            for (int i = 0; i < 3; i++) {
                lfDequant[i] = reader.readF16() * (1D / 128D);
            }
        }
        if (frame.getFrameHeader().encoding == FrameFlags.VARDCT) {
            quantizer = new Quantizer(reader);
            hfBlockCtx = new HFBlockContext(reader);
            lfChanCorr = new LFChannelCorrelation(reader);
        } else {
            quantizer = null;
            hfBlockCtx = null;
            lfChanCorr = null;
        }
        gModular = new GlobalModular(reader, frame);
    }
}
