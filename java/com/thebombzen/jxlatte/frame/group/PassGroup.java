package com.thebombzen.jxlatte.frame.group;

import java.io.IOException;

import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.frame.vardct.HFCoefficients;
import com.thebombzen.jxlatte.frame.vardct.TransformType;
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
        int groupBlockDim = frame.getFrameHeader().groupDim >> 3;
        LFGroup lfg = frame.getLFGroupForGroup(groupID);
        IntPoint groupBlockOrigin = frame.groupXY(groupID).times(groupBlockDim);
        IntPoint groupOrigin = groupBlockOrigin.shiftLeft(3);
        for (IntPoint blockPosBase : lfg.hfMetadata.blockList) {
            IntPoint blockPosBaseGroup = blockPosBase.minus(groupBlockOrigin);
            IntPoint blockPosBaseInGroup = blockPosBaseGroup.shiftLeft(3);
            if (blockPosBaseGroup.x >= groupBlockDim || blockPosBaseGroup.y >= groupBlockDim)
                continue; // this block is not in this group
            if (blockPosBaseGroup.x < 0 || blockPosBaseGroup.y < 0)
                continue; // this block is not in this group
            IntPoint[] shift = frame.getFrameHeader().jpegUpsampling;
            for (int c : Frame.cMap) {
                IntPoint blockPos = blockPosBase.shiftRight(shift[c]);
                if (!blockPos.shiftLeft(shift[c]).equals(blockPosBase))
                    continue; // subsampled block
                IntPoint blockPosInGroup = blockPosBaseInGroup.shiftRight(shift[c]);
                IntPoint blockPosInFrame = blockPosInGroup.plus(groupOrigin.shiftRight(shift[c]));
                TransformType tt = blockPos.get(lfg.hfMetadata.dctSelect);
                switch (tt.transformMethod) {
                    case TransformType.METHOD_DCT:
                        MathHelper.inverseDCT2D(coeffs[c], frameBuffer[c], blockPosInGroup.x, blockPosInFrame.x,
                            tt.blockWidth, blockPosInGroup.y, blockPosInFrame.y, tt.blockHeight);
                        if (!tt.isHorizontal()) {
                            IntPoint.iterate(tt.getBlockSize(), (p) -> {
                                if (p.x <= p.y)
                                    return;
                                IntPoint pos = blockPosInFrame.plus(p);
                                IntPoint transPos = blockPosInFrame.plus(p.transpose());
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
