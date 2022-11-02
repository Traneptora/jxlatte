package com.thebombzen.jxlatte.frame;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.IntUnaryOperator;

import com.thebombzen.jxlatte.ExceptionalSupplier;
import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.MathHelper;
import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.frame.lfglobal.LFGlobal;
import com.thebombzen.jxlatte.frame.modular.ModularChannel;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.io.IOHelper;
import com.thebombzen.jxlatte.io.InputStreamBitreader;

public class Frame {
    private Bitreader globalReader;
    private FrameHeader header;
    private int numGroups;
    private int numLFGroups;

    private byte[][] buffers;
    private int[] tocPermuation;
    private int[] tocLengths;
    private int[] groupOffsets;
    private LFGlobal lfGlobal;
    public final ImageHeader globalMetadata;
    private boolean permutedTOC;

    public Frame(Bitreader reader, ImageHeader globalMetadata) {
        this.globalReader = reader;
        this.globalMetadata = globalMetadata;
    }

    public void readHeader() throws IOException {
        this.header = new FrameHeader(globalReader, this.globalMetadata);
        int width = header.width;
        int height = header.height;
        width = MathHelper.ceilDiv(width, header.upsampling);
        height = MathHelper.ceilDiv(height, header.upsampling);
        width = MathHelper.ceilDiv(width, 1 << (3 * header.lfLevel));
        height = MathHelper.ceilDiv(height, 1 << (3 * header.lfLevel));
        numGroups = MathHelper.ceilDiv(width, header.groupDim) * MathHelper.ceilDiv(height, header.groupDim);
        numLFGroups = MathHelper.ceilDiv(width, header.groupDim << 3)
            * MathHelper.ceilDiv(height, header.groupDim << 3);
        readTOC();
    }

    public FrameHeader getFrameHeader() {
        return header;
    }

    public void skipFrameData() throws IOException {
        for (int i = 0; i < tocLengths.length; i++) {
            globalReader.skipBits(tocLengths[i]);
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

        permutedTOC = globalReader.readBool();
        if (permutedTOC) {          
            tocPermuation = readPermutation(globalReader, tocEntries, 0);
        } else {
            tocPermuation = new int[tocEntries];
            for (int i = 0; i < tocEntries; i++)
                tocPermuation[i] = i;
        }
        
        globalReader.zeroPadToByte();
        tocLengths = new int[tocEntries];
        buffers = new byte[tocEntries][];
        int[] unPermgroupOffsets = new int[tocEntries];
        for (int i = 0; i < tocEntries; i++) {
            if (i > 0)
                unPermgroupOffsets[i] = unPermgroupOffsets[i - 1] + tocLengths[i - 1];
            tocLengths[i] = globalReader.readU32(0, 10, 1024, 14, 17408, 22, 4211712, 30);
        }
        groupOffsets = new int[tocEntries];
        for (int i = 0; i < tocEntries; i++)
            groupOffsets[i] = unPermgroupOffsets[tocPermuation[i]];
        
        globalReader.zeroPadToByte();
    }

    private synchronized void readBuffer(int index) throws IOException {
        int length = tocLengths[index];
        buffers[index] = new byte[length];
        int read = globalReader.readBytes(buffers[index], 0, length);
        if (read < length)
            throw new EOFException("Unable to read full TOC entry");
    }

    private synchronized Bitreader getBitreader(int index) throws IOException {
        if (tocLengths.length == 1) {
            this.globalReader.zeroPadToByte();
            return this.globalReader;
        }
        int permutedIndex = tocPermuation[index];
        for (int i = 0; i <= permutedIndex; i++) {
            if (buffers[i] == null)
                readBuffer(i);
        }
        return new InputStreamBitreader(new ByteArrayInputStream(buffers[permutedIndex]));
    }

    public static int[] readPermutation(Bitreader reader, int size, int skip) throws IOException {
        EntropyStream stream = new EntropyStream(reader, 8);
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

    public double[][][] decodeFrame() throws IOException {

        lfGlobal = new LFGlobal(getBitreader(0), this);
        double[][][] buffer = new double[globalMetadata.getTotalChannelCount()][header.height][header.width];
        LFGroup[] lfGroups = new LFGroup[numLFGroups];

        List<ModularChannel> lfReplacementChannels = new ArrayList<>();
        List<Integer> lfReplacementChannelIndicies = new ArrayList<>();
        int lfRowStride = MathHelper.ceilDiv(header.width, header.groupDim << 3);
        for (int i = 0; i < lfGlobal.gModular.stream.getEncodedChannelCount(); i++) {
            ModularChannel chan = lfGlobal.gModular.stream.getChannel(i);
            if (!chan.isDecoded() && chan.hshift >= 3 && chan.vshift >= 3) {
                lfReplacementChannelIndicies.add(i);
                int width = header.groupDim >> (chan.hshift - 3);
                int height = header.groupDim >> (chan.vshift - 3);
                ModularChannel newChannel = new ModularChannel(width, height, chan.hshift, chan.vshift);
                lfReplacementChannels.add(newChannel);
            }
        }

        @SuppressWarnings("unchecked")
        CompletableFuture<LFGroup>[] lfGroupFutures = new CompletableFuture[numLFGroups];
        Bitreader[] lfBitreaders = new Bitreader[numLFGroups];

        for (int lfGroupID = 0; lfGroupID < numLFGroups; lfGroupID++) {
            lfBitreaders[lfGroupID] = getBitreader(1 + lfGroupID);
        }

        for (int lfGroupID = 0; lfGroupID < numLFGroups; lfGroupID++) {
            int row = lfGroupID / lfRowStride;
            int column = lfGroupID % lfRowStride;
            ModularChannel[] replaced = lfReplacementChannels.stream().map(ModularChannel::fromMetadata).toArray(ModularChannel[]::new);
            for (ModularChannel chan : replaced) {
                int lfWidth = MathHelper.ceilDiv(header.width, 1 << chan.hshift);
                int lfHeight = MathHelper.ceilDiv(header.height, 1 << chan.vshift);
                int x0 = column * chan.width;
                int y0 = row * chan.height;
                if (x0 + chan.width > lfWidth)
                    chan.width = lfWidth - x0;
                if (y0 + chan.height > lfHeight)
                    chan.height = lfHeight - y0;
                chan.x0 = x0;
                chan.y0 = y0;
            }
            lfGroups[lfGroupID] = new LFGroup(lfBitreaders[lfGroupID], this, lfGroupID, replaced);
        }

        for (int lfGroupID = 0; lfGroupID < numLFGroups; lfGroupID++) {
            for (int j = 0; j < lfReplacementChannelIndicies.size(); j++) {
                int index = lfReplacementChannelIndicies.get(j);
                ModularChannel channel = lfGlobal.gModular.stream.getChannel(index);
                if (!channel.isDecoded())
                    channel.allocate();
                ModularChannel newChannel = lfGroups[lfGroupID].lfStream.getChannel(j);
                for (int y = 0; y < newChannel.height; y++) {
                    for (int x = 0; x < newChannel.width; x++) {
                        channel.set(x + newChannel.x0, y + newChannel.y0, newChannel.get(x ,y));
                    }
                }
            }
        }

        if (header.encoding == FrameFlags.VARDCT) {
            throw new UnsupportedOperationException("VarDCT is not yet implemented");
        }

        int numPasses = header.passes.numPasses;
        Pass[] passes = new Pass[numPasses];
        PassGroup[][] passGroups = new PassGroup[numPasses][numGroups];
        for (int pass0 = 0; pass0 < numPasses; pass0++) {
            final int pass = pass0;
            passes[pass] = new Pass(this, pass, pass > 0 ? passes[pass - 1].minShift : 0);
            Bitreader[] bitreaders = new Bitreader[numGroups];
            for (int group = 0; group < numGroups; group++) {
                bitreaders[group] = getBitreader(2 + numLFGroups + pass * numGroups + group);
            }
            @SuppressWarnings("unchecked")
            CompletableFuture<PassGroup>[] futures = new CompletableFuture[numGroups];
            for (int group0 = 0; group0 < numGroups; group0++) {
                final int group = group0;
                futures[group] = CompletableFuture.supplyAsync(ExceptionalSupplier.uncheck(() -> {
                    ModularChannel[] replaced = Arrays.asList(passes[pass].replacedChannels)
                        .stream().map(ModularChannel::fromMetadata).toArray(ModularChannel[]::new);
                    for (ModularChannel chan : replaced) {
                        int passGroupWidth = header.groupDim >> chan.hshift;
                        int passGroupHeight = header.groupDim >> chan.vshift;
                        int rowStride = MathHelper.ceilDiv(chan.width, passGroupWidth);
                        int x0 = (group % rowStride) * passGroupWidth;
                        int y0 = (group / rowStride) * passGroupHeight;
                        int width = passGroupWidth;
                        int height = passGroupHeight;
                        if (width + x0 > chan.width) {
                            width = chan.width - x0;
                        }
                        if (height + y0 > chan.height) {
                            height = chan.height - y0;
                        }
                        chan.x0 = x0;
                        chan.y0 = y0;
                        chan.width = width;
                        chan.height = height;
                    }
                    return new PassGroup(bitreaders[group], Frame.this,
                        18 + 3 * numLFGroups + numGroups * pass + group, replaced);
                }));
            }
            for (int group = 0; group < numGroups; group++) {
                try {
                    passGroups[pass][group] = futures[group].join();
                } catch (CompletionException ex) {
                    IOHelper.sneakyThrow(ex.getCause());
                }
            }
        }

        for (int pass = 0; pass < numPasses; pass++) {
            int[] indices = passes[pass].replacedChannelIndices;
            for (int j = 0; j < indices.length; j++) {
                int index = indices[j];
                ModularChannel channel = lfGlobal.gModular.stream.getChannel(index);
                if (!channel.isDecoded())
                    channel.allocate();
                for (int group = 0; group < numGroups; group++) {
                    ModularChannel newChannel = passGroups[pass][group].stream.getChannel(j);
                    for (int y = 0; y < newChannel.height; y++) {
                        for (int x = 0; x < newChannel.width; x++) {
                            channel.set(x + newChannel.x0, y + newChannel.y0, newChannel.get(x, y));
                        }
                    }
                }
            }
        }

        lfGlobal.gModular.stream.applyTransforms();
        int[][][] streamBuffer = lfGlobal.gModular.stream.getDecodedBuffer();

        for (int c = 0; c < buffer.length; c++) {
            double scaleFactor;
            int cOut = c;
            boolean xyb = globalMetadata.isXYBEncoded();
            if (xyb) {
                if (c < 2) {
                    // X, Y, B is encoded as Y, X, (B - Y)
                    cOut = 1 - c;
                }
                scaleFactor = lfGlobal.lfDequant[cOut];
            } else if (globalMetadata.getBitDepthHeader().expBits != 0) {
                scaleFactor = 1.0D;
            } else {
                scaleFactor = 1.0D / ~(~0L << globalMetadata.getBitDepthHeader().bitsPerSample);
            }

            for (int y = 0; y < header.height; y++) {
                for (int x = 0; x < header.width; x++) {
                    if (xyb && c == 2) {
                        buffer[cOut][y][x] = scaleFactor * (streamBuffer[0][y][x] + streamBuffer[2][y][x]);
                    } else {
                        buffer[cOut][y][x] = scaleFactor * streamBuffer[c][y][x];
                    }
                }
            }
        }

        return buffer;
    }

    public LFGlobal getLFGlobal() {
        return lfGlobal;
    }

    public int getNumLFGroups() {
        return numLFGroups;
    }

    public int getNumGroups() {
        return numGroups;
    }
}
