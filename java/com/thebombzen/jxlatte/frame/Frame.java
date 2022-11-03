package com.thebombzen.jxlatte.frame;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntUnaryOperator;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.frame.lfglobal.LFGlobal;
import com.thebombzen.jxlatte.frame.modular.ModularChannel;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.io.InputStreamBitreader;
import com.thebombzen.jxlatte.util.FunctionalHelper;
import com.thebombzen.jxlatte.util.MathHelper;

public class Frame {
    private Bitreader globalReader;
    private FrameHeader header;
    private int numGroups;
    private int numLFGroups;

    private CompletableFuture<byte[]>[] buffers;
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

        {
            @SuppressWarnings("unchecked")
            CompletableFuture<byte[]>[] buffers = new CompletableFuture[tocEntries];
            this.buffers = buffers;
        }

        for (int i = 0; i < tocEntries; i++)
            buffers[i] = new CompletableFuture<>();

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

        new Thread(() -> {
            for (int i = 0; i < tocEntries; i++) {
                try {
                    byte[] buffer = readBuffer(i);
                    buffers[i].complete(buffer);
                } catch (Throwable ex) {
                    buffers[i].completeExceptionally(ex);
                }
            }
        }).start();
    }

    private byte[] readBuffer(int index) throws IOException {
        int length = tocLengths[index];
        byte[] buffer = new byte[length];
        int read = globalReader.readBytes(buffer, 0, length);
        if (read < length)
            throw new EOFException("Unable to read full TOC entry");
        return buffer;
    }

    private CompletableFuture<Bitreader> getBitreader(int index) throws IOException {
        if (tocLengths.length == 1) {
            this.globalReader.zeroPadToByte();
            return CompletableFuture.completedFuture(this.globalReader);
        }
        int permutedIndex = tocPermuation[index];
        return buffers[permutedIndex].thenApply((buff) -> {
            return new InputStreamBitreader(new ByteArrayInputStream(buff));
        });
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
        if (!stream.validateFinalState())
            throw new InvalidBitstreamException("Invalid stream decoding TOC");
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

        lfGlobal = new LFGlobal(getBitreader(0).join(), this);
        double[][][] buffer = new double[globalMetadata.getTotalChannelCount()][header.height][header.width];
        LFGroup[] lfGroups = new LFGroup[numLFGroups];

        List<ModularChannelInfo> lfReplacementChannels = new ArrayList<>();
        List<Integer> lfReplacementChannelIndicies = new ArrayList<>();
        int lfRowStride = MathHelper.ceilDiv(header.width, header.groupDim << 3);
        for (int i = 0; i < lfGlobal.gModular.stream.getEncodedChannelCount(); i++) {
            ModularChannel chan = lfGlobal.gModular.stream.getChannel(i);
            if (!chan.isDecoded()) {
                int hshift = chan.getInfo().hshift;
                int vshift = chan.getInfo().vshift;
                if (hshift >= 3 && vshift >= 3) {
                    lfReplacementChannelIndicies.add(i);
                    int width = header.groupDim >> (hshift - 3);
                    int height = header.groupDim >> (vshift - 3);
                    lfReplacementChannels.add(new ModularChannelInfo(width, height, hshift, vshift));
                }
            }
        }

        @SuppressWarnings("unchecked")
        CompletableFuture<LFGroup>[] lfGroupFutures = new CompletableFuture[numLFGroups];

        for (int lfGroupID0 = 0; lfGroupID0 < numLFGroups; lfGroupID0++) {
            final int lfGroupID = lfGroupID0;
            lfGroupFutures[lfGroupID] = CompletableFuture.supplyAsync(FunctionalHelper.uncheck(() -> {
                int row = lfGroupID / lfRowStride;
                int column = lfGroupID % lfRowStride;
                ModularChannelInfo[] replaced = lfReplacementChannels.stream().map(ModularChannelInfo::new)
                    .toArray(ModularChannelInfo[]::new);
                for (ModularChannelInfo info : replaced) {
                    int lfWidth = MathHelper.ceilDiv(header.width, 1 << info.hshift);
                    int lfHeight = MathHelper.ceilDiv(header.height, 1 << info.vshift);
                    int x0 = column * info.width;
                    int y0 = row * info.height;
                    if (x0 + info.width > lfWidth)
                        info.width = lfWidth - x0;
                    if (y0 + info.height > lfHeight)
                        info.height = lfHeight - y0;
                    info.x0 = x0;
                    info.y0 = y0;
                }
                return new LFGroup(getBitreader(1 + lfGroupID).join(), this, lfGroupID, replaced);
            }));
        }

        for (int lfGroupID = 0; lfGroupID < numLFGroups; lfGroupID++) {
            try {
                lfGroups[lfGroupID] = lfGroupFutures[lfGroupID].join();
            } catch (Throwable ex) {
                FunctionalHelper.sneakyThrow(ex);
            }
            for (int j = 0; j < lfReplacementChannelIndicies.size(); j++) {
                int index = lfReplacementChannelIndicies.get(j);
                ModularChannel channel = lfGlobal.gModular.stream.getChannel(index);
                ModularChannel newChannel = lfGroups[lfGroupID].lfStream.getChannel(j);
                ModularChannelInfo info = newChannel.getInfo();
                for (int y = 0; y < info.height; y++) {
                    for (int x = 0; x < info.width; x++) {
                        channel.set(x + info.x0, y + info.y0, newChannel.get(x ,y));
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
            @SuppressWarnings("unchecked")
            CompletableFuture<PassGroup>[] futures = new CompletableFuture[numGroups];
            for (int group0 = 0; group0 < numGroups; group0++) {
                final int group = group0;
                futures[group] = CompletableFuture.supplyAsync(FunctionalHelper.uncheck(() -> {
                    ModularChannelInfo[] replaced = Arrays.asList(passes[pass].replacedChannels)
                        .stream().map(ModularChannelInfo::new).toArray(ModularChannelInfo[]::new);
                    for (ModularChannelInfo info : replaced) {
                        int passGroupWidth = header.groupDim >> info.hshift;
                        int passGroupHeight = header.groupDim >> info.vshift;
                        int rowStride = MathHelper.ceilDiv(info.width, passGroupWidth);
                        int x0 = (group % rowStride) * passGroupWidth;
                        int y0 = (group / rowStride) * passGroupHeight;
                        int width = passGroupWidth;
                        int height = passGroupHeight;
                        if (width + x0 > info.width) {
                            width = info.width - x0;
                        }
                        if (height + y0 > info.height) {
                            height = info.height - y0;
                        }
                        info.x0 = x0;
                        info.y0 = y0;
                        info.width = width;
                        info.height = height;
                    }
                    return new PassGroup(getBitreader(2 + numLFGroups + pass * numGroups + group).join(), Frame.this,
                        18 + 3 * numLFGroups + numGroups * pass + group, replaced);
                }));
            }
            for (int group = 0; group < numGroups; group++) {
                try {
                    passGroups[pass][group] = futures[group].join();
                } catch (Throwable ex) {
                    FunctionalHelper.sneakyThrow(ex);
                }
            }
        }

        for (int pass = 0; pass < numPasses; pass++) {
            int[] indices = passes[pass].replacedChannelIndices;
            for (int j = 0; j < indices.length; j++) {
                int index = indices[j];
                ModularChannel channel = lfGlobal.gModular.stream.getChannel(index);
                for (int group = 0; group < numGroups; group++) {
                    ModularChannel newChannel = passGroups[pass][group].stream.getChannel(j);
                    ModularChannelInfo info = newChannel.getInfo();
                    for (int y = 0; y < info.height; y++) {
                        for (int x = 0; x < info.width; x++) {
                            channel.set(x + info.x0, y + info.y0, newChannel.get(x, y));
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
