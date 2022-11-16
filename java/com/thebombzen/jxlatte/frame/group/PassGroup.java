package com.thebombzen.jxlatte.frame.group;

import java.io.IOException;

import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.frame.vardct.HFCoefficients;
import com.thebombzen.jxlatte.frame.vardct.TransformType;
import com.thebombzen.jxlatte.frame.vardct.Varblock;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.IntPoint;
import com.thebombzen.jxlatte.util.MathHelper;

public class PassGroup {
    public final HFCoefficients hfCoefficients;
    public final ModularStream stream;
    public final Frame frame;
    public final int groupID;
    public final int passID;
    public PassGroup(Bitreader reader, Frame frame, int pass, int group,
            ModularChannelInfo[] replacedChannels) throws IOException {
        this.frame = frame;
        this.groupID = group;
        this.passID = pass;
        if (frame.getFrameHeader().encoding == FrameFlags.VARDCT) {
            hfCoefficients = new HFCoefficients(reader, frame, pass, group);
        } else {
            hfCoefficients = null;
        }
        stream = new ModularStream(reader, frame,
            18 + 3*frame.getNumLFGroups() + frame.getNumGroups()*pass + group, replacedChannels);
        stream.decodeChannels(reader);
    }

    public void invertVarDCT(double[][][] frameBuffer, PassGroup prev) {
        double[][][] coeffs = hfCoefficients.dequantHFCoeff;
        IntPoint[] sizes = IntPoint.sizeOf(coeffs);
        if (prev != null) {
            IntPoint.iterate(3, sizes, (c, x, y) -> {
                coeffs[c][y][x] += prev.hfCoefficients.dequantHFCoeff[c][y][x];
            });
        }
        IntPoint[] shift = frame.getFrameHeader().jpegUpsampling;
        for (Varblock varblock : hfCoefficients.varblocks) {
            for (int c : Frame.cMap) {
                if (!varblock.isCorner(shift[c]))
                    continue;
                TransformType tt = varblock.transformType();
                IntPoint pixelPosInFrame = varblock.pixelPosInFrame.shiftRight(shift[c]);
                switch (tt.transformMethod) {
                    case TransformType.METHOD_DCT:
                        MathHelper.inverseDCT2D(coeffs[c], frameBuffer[c], varblock.pixelPosInGroup, pixelPosInFrame, varblock.sizeInPixels());
                        if (!tt.isHorizontal()) {
                            IntPoint.iterate(tt.getBlockSize(), (p) -> {
                                if (p.x <= p.y)
                                    return;
                                IntPoint pos = pixelPosInFrame.plus(p);
                                IntPoint transPos = pixelPosInFrame.plus(p.transpose());
                                double tmp = pos.get(frameBuffer[c]);
                                pos.set(frameBuffer[c], transPos.get(frameBuffer[c]));
                                transPos.set(frameBuffer[c], tmp);
                            });
                        }
                        break;
                    default:
                        throw new UnsupportedOperationException("Transform not implemented: " + tt);
                }
            }
        }
    }
}
