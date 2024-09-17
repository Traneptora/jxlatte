package com.traneptora.jxlatte.frame;

import java.io.IOException;

import com.traneptora.jxlatte.color.ColorFlags;
import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.frame.features.Patch;
import com.traneptora.jxlatte.frame.features.noise.NoiseParameters;
import com.traneptora.jxlatte.frame.features.spline.SplinesBundle;
import com.traneptora.jxlatte.frame.modular.MATree;
import com.traneptora.jxlatte.frame.modular.ModularStream;
import com.traneptora.jxlatte.frame.vardct.HFBlockContext;
import com.traneptora.jxlatte.frame.vardct.LFChannelCorrelation;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.InvalidBitstreamException;

public class LFGlobal {
    public final Frame frame;
    public final Patch[] patches;
    public final SplinesBundle splines;
    public final NoiseParameters noiseParameters;
    public final float[] lfDequant = new float[]{1f / 4096f, 1f / 512f, 1f / 256f};
    public final int globalScale;
    public final int quantLF;
    public final float[] scaledDequant = new float[3];
    public final HFBlockContext hfBlockCtx;
    public final LFChannelCorrelation lfChanCorr;
    public final ModularStream globalModular;

    public LFGlobal(Bitreader reader, Frame parent) throws IOException {
        this.frame = parent;
        int extra = frame.globalMetadata.getExtraChannelCount();
        FrameHeader header = frame.getFrameHeader();
        if ((header.flags & FrameFlags.PATCHES) != 0) {
            EntropyStream stream = new EntropyStream(frame.getLoggers(), reader, 10);
            int numPatches = stream.readSymbol(reader, 0);
            patches = new Patch[numPatches];
            for (int i = 0; i < numPatches; i++) {
                patches[i] = new Patch(stream, reader, extra, parent.globalMetadata.getNumAlphaChannels());
            }
            if (!stream.validateFinalState())
                throw new InvalidBitstreamException("Illegal ANS final state");
        } else {
            patches = new Patch[0];
        }
        if ((header.flags & FrameFlags.SPLINES) != 0) {
            if (frame.globalMetadata.getColorChannelCount() < 3)
                throw new InvalidBitstreamException("Cannot do splines in grayscale");
            splines = new SplinesBundle(frame.getLoggers(), reader);
        } else {
            splines = null;
        }
        if ((header.flags & FrameFlags.NOISE) != 0) {
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
        if (header.encoding == FrameFlags.VARDCT) {
            globalScale = reader.readU32(1, 11, 2049, 11, 4097, 12, 8193, 16);
            quantLF = reader.readU32(16, 0, 1, 5, 1, 8, 1, 16);
            for (int i = 0; i < 3; i++)
                scaledDequant[i] =  (1 << 16) * lfDequant[i] / (globalScale * quantLF);
            hfBlockCtx = new HFBlockContext(reader, parent.getLoggers());
            lfChanCorr = new LFChannelCorrelation(reader);
        } else {
            globalScale = 0;
            quantLF = 0;
            hfBlockCtx = null;
            // default value used for splines and noise in modular mode
            lfChanCorr = new LFChannelCorrelation();
        }

        boolean hasGlobalTree = reader.readBool();
        MATree globalTree = hasGlobalTree ? new MATree(parent.getLoggers(), reader) : null;
        frame.setGlobalTree(globalTree);
        int subModularChannelCount = extra;
        int ecStart = 0;
        if (header.encoding == FrameFlags.MODULAR) {
            if (!header.doYCbCr && !frame.globalMetadata.isXYBEncoded()
                    && frame.globalMetadata.getColorEncoding().colorEncoding == ColorFlags.CE_GRAY)
                ecStart = 1;
            else
                ecStart = 3;
        }
        subModularChannelCount += ecStart;
        globalModular = new ModularStream(reader, parent, 0, subModularChannelCount, ecStart);
        globalModular.decodeChannels(reader, true);
    }
}
