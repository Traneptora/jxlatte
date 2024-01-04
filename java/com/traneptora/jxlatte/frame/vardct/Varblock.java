package com.traneptora.jxlatte.frame.vardct;

import com.traneptora.jxlatte.frame.group.LFGroup;
import com.traneptora.jxlatte.util.IntPoint;

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
    // group ID of this varblock's group
    public final int groupID;

    public final LFGroup lfGroup;
    
    public Varblock(LFGroup lfGroup, IntPoint blockPosInLFGroup) {
        this.lfGroup = lfGroup;
        this.blockPosInLFGroup = blockPosInLFGroup;
        pixelPosInLFGroup = new IntPoint(blockPosInLFGroup.x << 3, blockPosInLFGroup.y << 3);
        groupPosInLFGroup = blockPosInLFGroup.shiftRight(5);
        int x = blockPosInLFGroup.x - (groupPosInLFGroup.x << 5);
        int y = blockPosInLFGroup.y - (groupPosInLFGroup.y << 5);
        blockPosInGroup = new IntPoint(x, y);
        pixelPosInGroup = new IntPoint(x << 3, y << 3);
        IntPoint lfgXY = lfGroup.frame.getLFGroupXY(lfGroup.lfGroupID);
        x = (lfgXY.x << 3) + groupPosInLFGroup.x;
        y = (lfgXY.y << 3) + groupPosInLFGroup.y;
        groupID = y * lfGroup.frame.getGroupRowStride() + x;
    }

    public TransformType transformType() {
        return lfGroup.hfMetadata.dctSelect[blockPosInLFGroup.y][blockPosInLFGroup.x];
    }

    public int hfMult() {
        return lfGroup.hfMetadata.hfMultiplier[blockPosInLFGroup.y][blockPosInLFGroup.x];
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
        final int x = blockPosInLFGroup.x >> shift.x;
        final int y = blockPosInLFGroup.y >> shift.y;
        return (x << shift.x) == blockPosInLFGroup.x && (y << shift.y) == blockPosInLFGroup.y;
    }
}
