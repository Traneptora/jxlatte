package com.thebombzen.jxlatte.frame;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import com.thebombzen.jxlatte.bundle.Extensions;
import com.thebombzen.jxlatte.io.Bitreader;

public class RestorationFilter {

    public final boolean gab;
    public final boolean customGab;
    public final float[] gab1Weights = new float[]{0.115169525f, 0.115169525f, 0.115169525f};
    public final float[] gab2Weights = new float[]{0.061248592f, 0.061248592f, 0.061248592f};
    public final int epfIterations;
    public final boolean epfSharpCustom;
    public final float[] epfSharpLut = new float[]{0f, 1f/7f, 2f/7f, 3f/7f, 4f/7f, 5f/7f, 6f/7f, 1f};
    public final boolean epfWeightCustom;
    public final float[] epfChannelScale = new float[]{40.0f, 5.0f, 3.5f};
    public final boolean epfSigmaCustom;
    public final float epfQuantMul;
    public final float epfPass0SigmaScale;
    public final float epfPass2SigmaScale;
    public final float epfBorderSadMul;
    public final float epfSigmaForModular;
    public final Extensions extensions;

    public RestorationFilter() {
        gab = true;
        customGab = false;
        epfIterations = 2;
        epfSharpCustom = false;
        epfWeightCustom = false;
        epfSigmaCustom = false;
        epfQuantMul = 0.46f;
        epfPass0SigmaScale = 0.9f;
        epfPass2SigmaScale = 6.5f;
        epfBorderSadMul = 2f/3f;
        epfSigmaForModular = 1.0f;
        extensions = new Extensions();
    }

    public RestorationFilter(Bitreader reader, int encoding) throws IOException {
        boolean allDefault = reader.readBool();
        gab = allDefault ? true : reader.readBool();
        customGab = gab ? reader.readBool() : false;
        if (customGab) {
            for (int i = 0; i < 3; i++) {
                gab1Weights[i] = reader.readF16();
                gab2Weights[i] = reader.readF16();
            }
        }
        epfIterations = allDefault ? 2 : reader.readBits(2);
        epfSharpCustom = !allDefault && epfIterations > 0 && encoding == FrameFlags.VARDCT ?
            reader.readBool() : false;
        if (epfSharpCustom) {
            for (int i = 0; i < epfSharpLut.length; i++)
                epfSharpLut[i] = reader.readF16();
        }
        epfWeightCustom = !allDefault && epfIterations > 0 ? reader.readBool() : false;
        if (epfWeightCustom) {
            for (int i = 0; i < epfChannelScale.length; i++)
                epfChannelScale[i] = reader.readF16();
            reader.readBits(32); // ok
        }
        epfSigmaCustom = !allDefault && epfIterations > 0 ? reader.readBool() : false;
        epfQuantMul = epfSigmaCustom && encoding == FrameFlags.VARDCT ? reader.readF16() : 0.46f;
        epfPass0SigmaScale = epfSigmaCustom ? reader.readF16() : 0.9f;
        epfPass2SigmaScale = epfSigmaCustom ? reader.readF16() : 6.5f;
        epfBorderSadMul = epfSigmaCustom ? reader.readF16() : 2f/3f;
        epfSigmaForModular = !allDefault && epfIterations > 0 && encoding == FrameFlags.MODULAR
            ? reader.readF16() : 1.0f;
        extensions = allDefault ? new Extensions() : new Extensions(reader);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(gab1Weights);
        result = prime * result + Arrays.hashCode(gab2Weights);
        result = prime * result + Arrays.hashCode(epfSharpLut);
        result = prime * result + Arrays.hashCode(epfChannelScale);
        result = prime * result + Objects.hash(gab, customGab, epfIterations, epfSharpCustom, epfWeightCustom,
                epfSigmaCustom, epfQuantMul, epfPass0SigmaScale, epfPass2SigmaScale, epfBorderSadMul,
                epfSigmaForModular, extensions);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RestorationFilter other = (RestorationFilter) obj;
        return gab == other.gab && customGab == other.customGab && Arrays.equals(gab1Weights, other.gab1Weights)
                && Arrays.equals(gab2Weights, other.gab2Weights) && epfIterations == other.epfIterations
                && epfSharpCustom == other.epfSharpCustom && Arrays.equals(epfSharpLut, other.epfSharpLut)
                && epfWeightCustom == other.epfWeightCustom && Arrays.equals(epfChannelScale, other.epfChannelScale)
                && epfSigmaCustom == other.epfSigmaCustom
                && Float.floatToIntBits(epfQuantMul) == Float.floatToIntBits(other.epfQuantMul)
                && Float.floatToIntBits(epfPass0SigmaScale) == Float.floatToIntBits(other.epfPass0SigmaScale)
                && Float.floatToIntBits(epfPass2SigmaScale) == Float.floatToIntBits(other.epfPass2SigmaScale)
                && Float.floatToIntBits(epfBorderSadMul) == Float.floatToIntBits(other.epfBorderSadMul)
                && Float.floatToIntBits(epfSigmaForModular) == Float.floatToIntBits(other.epfSigmaForModular)
                && Objects.equals(extensions, other.extensions);
    }

    @Override
    public String toString() {
        return "RestorationFilter [gab=" + gab + ", customGab=" + customGab + ", gab1Weights="
                + Arrays.toString(gab1Weights) + ", gab2Weights=" + Arrays.toString(gab2Weights) + ", epfIterations="
                + epfIterations + ", epfSharpCustom=" + epfSharpCustom + ", epfSharpLut=" + Arrays.toString(epfSharpLut)
                + ", epfWeightCustom=" + epfWeightCustom + ", epfChannelScale=" + Arrays.toString(epfChannelScale)
                + ", epfSigmaCustom=" + epfSigmaCustom + ", epfQuantMul=" + epfQuantMul + ", epfPass0SigmaScale="
                + epfPass0SigmaScale + ", epfPass2SigmaScale=" + epfPass2SigmaScale + ", epfBorderSadMul="
                + epfBorderSadMul + ", epfSigmaForModular=" + epfSigmaForModular + ", extensions=" + extensions + "]";
    }
}
