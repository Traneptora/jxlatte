package com.thebombzen.jxlatte.frame;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.awt.Point;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.MathHelper;
import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.entropy.EntropyDecoder;
import com.thebombzen.jxlatte.frame.lfglobal.LFGlobal;
import com.thebombzen.jxlatte.frame.modular.ModularStream;
import com.thebombzen.jxlatte.io.Bitreader;

public class Frame {
    private Bitreader reader;
    private FrameHeader header;
    private int numGroups;
    private int numLFGroups;
    private int[] tocPermuation;
    private int[] tocLengths;
    private int[] groupOffsets;
    private LFGlobal lfGlobal;
    public final ImageHeader globalMetadata;

    public Frame(Bitreader reader, ImageHeader globalMetadata) {
        this.reader = reader;
        this.globalMetadata = globalMetadata;
    }

    public void readHeader() throws IOException {
        this.header = new FrameHeader(reader, this.globalMetadata);
        int width = header.width;
        int height = header.height;
        width = Math.ceilDiv(width, header.upsampling);
        height = Math.ceilDiv(height, header.upsampling);
        width = Math.ceilDiv(width, 1 << (3 * header.lfLevel));
        height = Math.ceilDiv(height, 1 << (3 * header.lfLevel));
        int groupDim = 128 << header.groupSizeShift;
        numGroups = Math.ceilDiv(width, groupDim) * Math.ceilDiv(height, groupDim);
        numLFGroups = Math.ceilDiv(width, groupDim * 8) * Math.ceilDiv(height, groupDim * 8);
        readTOC();
    }

    public FrameHeader getFrameHeader() {
        return header;
    }

    public void skipFrameData() throws IOException {
        for (int i = 0; i < tocLengths.length; i++) {
            reader.skipBits(tocLengths[i]);
        }
    }

    private void readTOC() throws IOException {
        int tocEntries;
        if (numGroups == 1 && header.passes.numPasses == 1) {
            tocEntries = 1;
        } else {
            // lfGlobal + one per LfGroup + HfGlobal + HfPassData
            tocEntries = 1 + numLFGroups + 1 + numGroups * header.passes.numPasses;
        }

        if (reader.readBool()) {          
            tocPermuation = readPermutation(reader, tocEntries, 0);
        } else {
            tocPermuation = new int[tocEntries];
            for (int i = 0; i < tocEntries; i++)
                tocPermuation[i] = i;
        }
        
        reader.zeroPadToByte();
        tocLengths = new int[tocEntries];
        int[] unPermgroupOffsets = new int[tocEntries];
        for (int i = 0; i < tocEntries; i++) {
            if (i > 0)
                unPermgroupOffsets[i] = unPermgroupOffsets[i - 1] + tocLengths[i - 1];
            tocLengths[i] = reader.readU32(0, 10, 1024, 14, 17408, 22, 4211712, 30);
        }
        groupOffsets = new int[tocEntries];
        for (int i = 0; i < tocEntries; i++)
            groupOffsets[i] = unPermgroupOffsets[tocPermuation[i]];
        reader.zeroPadToByte();
    }

    public static int[] readPermutation(Bitreader reader, int size, int skip) throws IOException {
        EntropyDecoder stream = new EntropyDecoder(reader, 8);
        IntUnaryOperator ctx = x -> Math.min(7, MathHelper.ceilLog1p(x));
        int end = stream.readSymbol(reader, ctx.applyAsInt(size));
        if (end > size - skip)
            throw new InvalidBitstreamException("Illegal end value in lehmer sequence");
        int[] lehmer = new int[skip + end];
        for (int i = skip; i < end + skip; i++) {
            lehmer[i] = stream.readSymbol(reader, ctx.applyAsInt(i > skip ? lehmer[i - 1] : 0));
            if (lehmer[i] >= size - i)
                throw new InvalidBitstreamException("Illegal lehmer value in lehmer sequence");
        }
        List<Integer> temp = new LinkedList<Integer>();
        int[] permutation = new int[size];
        for (int i = 0; i < size; i++)
            temp.add(i);
        for (int i = 0; i < size; i++) {
            int index = lehmer[i];
            permutation[i] = temp.remove(index);
        }
        return permutation;
    }

    public WritableRaster decodeFrame() throws IOException {
        lfGlobal = new LFGlobal(reader, this);
        if (header.groupDim > header.width && header.groupDim > header.height) {
            ModularStream stream = lfGlobal.gModular.stream;
            stream.applyTransforms();
            stream.clamp();
            int[][][] channels = stream.getDecodedBuffer();
            WritableRaster raster = Raster.createBandedRaster(DataBuffer.TYPE_INT, header.width, header.height, channels.length, new Point(header.x0, header.y0));
            for (int i = 0; i < channels.length; i++) {
                for (int y = 0; y < channels[i].length; y++) {
                    raster.setSamples(0, y, channels[i][y].length, 1, i, channels[i][y]);
                }               
            }
            return raster;
        } else {
            throw new UnsupportedOperationException("LF Groups not yet implemented");
        }
    }
}
