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
    // position, int units of pixels, relative to LF Group origin
    public final IntPoint pixelPosInLFGroup;

    private LFGroup lfGroup;
    
    public Varblock(LFGroup lfGroup, IntPoint blockPosInLFGroup) {
        this.lfGroup = lfGroup;
        this.blockPosInLFGroup = blockPosInLFGroup;
        pixelPosInLFGroup = new IntPoint(blockPosInLFGroup.x << 3, blockPosInLFGroup.y << 3);
        groupPosInLFGroup = blockPosInLFGroup.shiftRight(5);
        int x = blockPosInLFGroup.x - (groupPosInLFGroup.x << 5);
        int y = blockPosInLFGroup.y - (groupPosInLFGroup.y << 5);
        blockPosInGroup = new IntPoint(x, y);
        pixelPosInGroup = new IntPoint(x << 3, y << 3);
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
        return transformType().getPixelSize();
    }

    public boolean flip() {
        return transformType().flip();
    }

    public boolean isCorner(IntPoint shift) {
        return blockPosInLFGroup.shiftRight(shift).shiftLeft(shift).equals(blockPosInLFGroup);
    }
}
