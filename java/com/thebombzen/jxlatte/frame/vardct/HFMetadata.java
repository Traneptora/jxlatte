package com.thebombzen.jxlatte.frame.vardct;

import java.io.IOException;
import java.util.Arrays;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameHeader;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.IntPoint;
import com.thebombzen.jxlatte.util.MathHelper;

public class HFMetadata {
    public final int nbBlocks;
    public final TransformType[][] dctSelect;
    public final int[][] hfMultiplier;
    public final ModularStream hfStream;

    private IntPoint blockXY = new IntPoint();

    public HFMetadata(Bitreader reader, Frame frame, int streamIndex) throws IOException {
        FrameHeader header = frame.getFrameHeader();
        int width = MathHelper.ceilDiv(header.width, 8);
        int height = MathHelper.ceilDiv(header.height, 8);
        int n = MathHelper.ceilLog1p(width * height - 1);
        nbBlocks = 1 + reader.readBits(n);
        int aFromYWidth = MathHelper.ceilDiv(header.width, 64);
        int aFromYHeight = MathHelper.ceilDiv(header.height, 64);
        ModularChannelInfo xFromY = new ModularChannelInfo(aFromYWidth, aFromYHeight, 0, 0);
        ModularChannelInfo bFromY = new ModularChannelInfo(aFromYWidth, aFromYHeight, 0, 0);
        ModularChannelInfo blockInfo = new ModularChannelInfo(nbBlocks, 2, 0, 0);
        ModularChannelInfo sharpness = new ModularChannelInfo(width, height, 0, 0);
        hfStream = new ModularStream(reader, frame.getLFGlobal().gModular.globalTree, frame, streamIndex,
            new ModularChannelInfo[]{xFromY, bFromY, blockInfo, sharpness});
        hfStream.decodeChannels(reader, false);
        dctSelect = new TransformType[height][width];
        hfMultiplier = new int[height][width];
        int[][] blockInfoBuffer = hfStream.getDecodedBuffer()[2];
        for (int i = 0; i < nbBlocks; i++) {
            int type = blockInfoBuffer[0][i];
            if (type > 26 || type < 0)
                throw new InvalidBitstreamException("Invalid Transform Type: " + type);
            placeBlock(TransformType.get(type), 1 + blockInfoBuffer[1][i]);
        }
    }

    private void placeBlock(TransformType block, int mul) throws InvalidBitstreamException {
        for (; blockXY.y < dctSelect.length; blockXY.y++) {
            int y = blockXY.y;
            for (; blockXY.x < dctSelect[y].length; blockXY.x++) {
                int x = blockXY.x;
                // space occupied
                if (dctSelect[y][x] != null)
                    continue;
                // block too big, horizontally, to put here
                if (block.dctSelectWidth + x > dctSelect[y].length)
                    continue;
                // block too big, vertically, to put here
                if (block.dctSelectHeight + y > dctSelect.length)
                    continue;
                for (int iy = 0; iy < block.dctSelectHeight; iy++)
                    Arrays.fill(dctSelect[y + iy], x, x + block.dctSelectWidth, block);
                hfMultiplier[y][x] = mul;
                return;
            }
            blockXY.x = 0;
        }
        throw new InvalidBitstreamException("Could not find place for block");
    }
}
