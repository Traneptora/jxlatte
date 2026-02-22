package com.traneptora.jxlatte.frame.vardct;

import java.io.IOException;
import java.util.Arrays;

import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.group.LFGroup;
import com.traneptora.jxlatte.frame.modular.ModularChannel;
import com.traneptora.jxlatte.frame.modular.ModularStream;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.InvalidBitstreamException;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.Point;

public class HFMetadata {
    public final int nbBlocks;
    public final TransformType[][] dctSelect;
    public final int[][] hfMultiplier;
    public final int[][][] hfStreamBuffer;
    public final LFGroup parent;
    public final Point[] blockList;

    public HFMetadata(Bitreader reader, LFGroup parent, Frame frame) throws IOException {
        this.parent = parent;
        int n = MathHelper.ceilLog2(parent.size.height * parent.size.width);
        nbBlocks = 1 + reader.readBits(n);
        int correlationHeight = (parent.size.height + 7) / 8;
        int correlationWidth = (parent.size.width + 7) / 8;
        ModularChannel xFromY = new ModularChannel(correlationHeight, correlationWidth, 0, 0);
        ModularChannel bFromY = new ModularChannel(correlationHeight, correlationWidth, 0, 0);
        ModularChannel blockInfo = new ModularChannel(2, nbBlocks, 0, 0);
        ModularChannel sharpness = new ModularChannel(parent.size.height, parent.size.width, 0, 0);
        ModularStream hfStream = new ModularStream(reader, frame, 1 + 2*frame.getNumLFGroups() + parent.lfGroupID,
            new ModularChannel[]{xFromY, bFromY, blockInfo, sharpness});
        hfStream.decodeChannels(reader);
        hfStreamBuffer = hfStream.getDecodedBuffer();
        hfStream = null;
        dctSelect = new TransformType[parent.size.height][parent.size.width];
        hfMultiplier = new int[parent.size.height][parent.size.width];
        int[][] blockInfoBuffer = hfStreamBuffer[2];
        Point lastBlock = new Point();
        TransformType[] tta = TransformType.values();
        blockList = new Point[nbBlocks];
        for (int i = 0; i < nbBlocks; i++) {
            int type = blockInfoBuffer[0][i];
            if (type > 26 || type < 0)
                throw new InvalidBitstreamException("Invalid Transform Type: " + type);
            TransformType tt = tta[type];
            Point pos = placeBlock(lastBlock, tt, 1 + blockInfoBuffer[1][i]);
            lastBlock = new Point(pos);
            blockList[i] = pos;
        }
    }

    public String getBlockMapAsciiArt() {
        String[][] strings = new String[2 * dctSelect.length + 1][2 * dctSelect[0].length + 1];
        int k = 0;
        for (Point block : blockList) {
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

    private Point placeBlock(Point lastBlock, TransformType block, int mul) throws InvalidBitstreamException {
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
                Point pos = new Point(y, x);
                for (int iy = 0; iy < block.dctSelectHeight; iy++) {
                    Arrays.fill(dctSelect[y + iy], x, x + block.dctSelectWidth, block);
                    Arrays.fill(hfMultiplier[y + iy], x, x + block.dctSelectWidth, mul);
                }
                return pos;
            }
        }
        throw new InvalidBitstreamException("Could not find place for block: " + lastBlock);
    }
}
