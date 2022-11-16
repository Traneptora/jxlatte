package com.thebombzen.jxlatte.frame.vardct;

import com.thebombzen.jxlatte.frame.group.LFGroup;
import com.thebombzen.jxlatte.util.IntPoint;

public class Varblock {
    // position, in units of blocks, relative to LF group origin
    public final IntPoint blockPosInLFGroup;
    // position, in units of groups (rounded down), relative to LF group origin
    public final IntPoint groupPosInLFGroup;
    // position, in units of blocks, relative to group origin
    public final IntPoint blockPosInGroup;
    // position, in units of pixels, relative to group origin
    public final IntPoint pixelPosInGroup;
    // position, in units of pixels, relative to frame origin
    public final IntPoint pixelPosInFrame;
    // position, in units of groups (rounded down), relative to frame origin
    public final IntPoint groupPosInFrame;
    // position, int units of pixels, relative to LF Group origin
    public final IntPoint pixelPosInLFGroup;

    private LFGroup lfGroup;
    
    public Varblock(LFGroup lfGroup, IntPoint blockPosInLFGroup) {
        this.lfGroup = lfGroup;
        this.blockPosInLFGroup = blockPosInLFGroup;
        int groupBlockDim = lfGroup.frame.getGroupBlockDim();
        groupPosInLFGroup = blockPosInLFGroup.divide(groupBlockDim);
        blockPosInGroup = blockPosInLFGroup.minus(groupPosInLFGroup.times(groupBlockDim));
        pixelPosInGroup = blockPosInGroup.shiftLeft(3);
        groupPosInFrame = lfGroup.frame.getLFGroupXY(lfGroup.lfGroupID).shiftLeft(3).plus(groupPosInLFGroup);
        pixelPosInFrame = groupPosInFrame.times(lfGroup.frame.getFrameHeader().groupDim).plus(pixelPosInGroup);
        pixelPosInLFGroup = groupPosInLFGroup.times(lfGroup.frame.getFrameHeader().groupDim).plus(pixelPosInGroup);
    }

    public boolean isHorizontal() {
        return transformType().isHorizontal();
    }

    public TransformType transformType() {
        return blockPosInLFGroup.get(lfGroup.hfMetadata.dctSelect);
    }

    public int hfMult() {
        return blockPosInLFGroup.get(lfGroup.hfMetadata.hfMultiplier);
    }

    public IntPoint sizeInBlocks() {
        TransformType tt = transformType();
        return new IntPoint(tt.dctSelectWidth, tt.dctSelectHeight);
    }

    public IntPoint sizeInPixels() {
        return transformType().getBlockSize();
    }

    public boolean isCorner(IntPoint shift) {
        return blockPosInLFGroup.shiftRight(shift).shiftLeft(shift).equals(blockPosInLFGroup);
    }
}
