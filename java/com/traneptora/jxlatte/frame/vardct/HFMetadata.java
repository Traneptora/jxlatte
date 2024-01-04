package com.traneptora.jxlatte.frame.vardct;

import java.io.IOException;
import java.util.Arrays;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.group.LFGroup;
import com.traneptora.jxlatte.frame.modular.ModularChannelInfo;
import com.traneptora.jxlatte.frame.modular.ModularStream;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.util.IntPoint;
import com.traneptora.jxlatte.util.MathHelper;

public class HFMetadata {
    public final int nbBlocks;
    public final TransformType[][] dctSelect;
    public final IntPoint[] blockList;
    public final Varblock[][] blockMap;
    public final int[][] hfMultiplier;
    public final int[][][] hfStreamBuffer;
    public final LFGroup parent;

    public HFMetadata(Bitreader reader, LFGroup parent, Frame frame) throws IOException {
        this.parent = parent;
        final IntPoint size = frame.getLFGroupSize(parent.lfGroupID).shiftRight(3);
        int n = MathHelper.ceilLog2(size.x * size.y);
        nbBlocks = 1 + reader.readBits(n);
        IntPoint aFromYSize = size.ceilDiv(8);
        ModularChannelInfo xFromY = new ModularChannelInfo(aFromYSize.x, aFromYSize.y, 0, 0);
        ModularChannelInfo bFromY = new ModularChannelInfo(aFromYSize.x, aFromYSize.y, 0, 0);
        ModularChannelInfo blockInfo = new ModularChannelInfo(nbBlocks, 2, 0, 0);
        ModularChannelInfo sharpness = new ModularChannelInfo(size.x, size.y, 0, 0);
        ModularStream hfStream = new ModularStream(reader, frame, 1 + 2*frame.getNumLFGroups() + parent.lfGroupID,
            new ModularChannelInfo[]{xFromY, bFromY, blockInfo, sharpness});
        hfStream.decodeChannels(reader);
        hfStreamBuffer = hfStream.getDecodedBuffer();
        hfStream = null;
        dctSelect = new TransformType[size.y][size.x];
        hfMultiplier = new int[size.y][size.x];
        blockList = new IntPoint[nbBlocks];
        blockMap = new Varblock[size.y][size.x];
        final int[][] blockInfoBuffer = hfStreamBuffer[2];
        IntPoint lastBlock = new IntPoint();
        final TransformType[] tta = TransformType.values();
        for (int i = 0; i < nbBlocks; i++) {
            final int type = blockInfoBuffer[0][i];
            if (type > 26 || type < 0)
                throw new InvalidBitstreamException("Invalid Transform Type: " + type);
            final TransformType tt = tta[type];
            final IntPoint pos = placeBlock(lastBlock, tt, 1 + blockInfoBuffer[1][i]);
            lastBlock = pos;
            blockList[i] = pos;
            final Varblock varblock = new Varblock(parent, pos);
            for (int y = 0; y < tt.dctSelectHeight; y++)
                Arrays.fill(blockMap[y + pos.y], pos.x, pos.x + tt.dctSelectWidth, varblock);
        }
    }

    public String getBlockMapAsciiArt() {
        String[][] strings = new String[2 * dctSelect.length + 1][2 * dctSelect[0].length + 1];
        int k = 0;
        for (IntPoint block : blockList) {
            int dw = dctSelect[block.y][block.x].dctSelectWidth;
            int dh = dctSelect[block.y][block.x].dctSelectHeight;
            strings[2*block.y + 1][2*block.x + 1] = String.format("%03d", k++ % 1000);
            for (int x = 0; x < dw; x++) {
                strings[2*block.y][2*(block.x + x)] = "+";
                strings[2*block.y][2*(block.x + x) + 1] = "---";
                strings[2*(block.y+dh)][2*(block.x + x)] = "+";
                strings[2*(block.y+dh)][2*(block.x + x)+1] = "---";
            }
            for (int y = 0; y < dh; y++) {
                strings[2*(block.y + y)][2*block.x] = "+";
                strings[2*(block.y + y) + 1][2*block.x] = "|";
                strings[2*(block.y + y)][2*(block.x+dw)] = "+";
                strings[2*(block.y + y) + 1][2*(block.x+dw)] = "|";
            }
            strings[2*(block.y + dh)][2*(block.x + dw)] = "+";
        }
        StringBuilder builder = new StringBuilder();
        for (int y = 0; y < strings.length; y++) {
            for (int x = 0; x < strings[y].length; x++) {
                String s = strings[y][x];
                if (s == null) {
                    if (x % 2 == 0)
                        s = " ";
                    else
                        s = "   ";
                }
                builder.append(s);
            }
            builder.append(String.format("%n"));
        }
        return builder.toString();
    }

    private IntPoint placeBlock(IntPoint lastBlock, TransformType block, int mul) throws InvalidBitstreamException {
        outerY:
        for (int y = lastBlock.y, x = lastBlock.x; y < dctSelect.length; y++, x = 0) {
            final TransformType[] dctY = dctSelect[y];
            outerX:
            for (; x < dctY.length; x++) {
                // block too big to put here
                if (block.dctSelectWidth + x > dctY.length)
                    continue outerY;
                // space occupied
                for (int ix = 0; ix < block.dctSelectWidth; ix++) {
                    final TransformType tt = dctY[x + ix];
                    if (tt != null) {
                        x += tt.dctSelectWidth - 1;
                        continue outerX;
                    }
                }
                final IntPoint pos = new IntPoint(x, y);
                hfMultiplier[y][x] = mul;
                for (int iy = 0; iy < block.dctSelectHeight; iy++)
                    Arrays.fill(dctSelect[y + iy], x, x + block.dctSelectWidth, block);
                return pos;
            }
        }
        throw new InvalidBitstreamException("Could not find place for block: " + lastBlock);
    }

    public Varblock getVarblock(int i) {
        final IntPoint block = blockList[i];
        return blockMap[block.y][block.x];
    }
}
