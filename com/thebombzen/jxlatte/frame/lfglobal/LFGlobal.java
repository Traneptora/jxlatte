package com.thebombzen.jxlatte.frame.lfglobal;

import java.io.IOException;

import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.io.Bitreader;

public class LFGlobal {
    public final Frame frame;
    public final Patches patches;
    public final Splines splines;
    public final NoiseParamters noiseParamters;
    public final float[] lfDequant = new float[]{4096f, 512f, 256f};
    public final Quantizer quantizer;
    public final HFBlockContext hfBlockCtx;
    public final LFChannelCorrelation lfChanCorr;
    public final GlobalModular gModular;


    public LFGlobal(Bitreader reader, Frame parent) throws IOException {
        this.frame = parent;
        if ((frame.getFrameHeader().flags & FrameFlags.PATCHES) != 0) {
            patches = new Patches(reader);
        } else {
            patches = null;
        }
        if ((frame.getFrameHeader().flags & FrameFlags.SPLINES) != 0) {
            splines = new Splines(reader);
        } else {
            splines = null;
        }
        if ((frame.getFrameHeader().flags & FrameFlags.NOISE) != 0) {
            noiseParamters = new NoiseParamters(reader);
        } else {
            noiseParamters = null;
        }
        if (!reader.readBool()) {
            for (int i = 0; i < 3; i++) {
                lfDequant[i] = reader.readF16();
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
