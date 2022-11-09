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
import com.thebombzen.jxlatte.util.IntPoint;
import com.thebombzen.jxlatte.util.MathHelper;
import com.thebombzen.jxlatte.util.TaskList;

public class Frame {
    private Bitreader globalReader;
    private FrameHeader header;
    private int numGroups;
    private int numLFGroups;

    private CompletableFuture<byte[]>[] buffers;
    private int[] tocPermuation;
    private int[] tocLengths;
    private LFGlobal lfGlobal;
    public final ImageHeader globalMetadata;
    private boolean permutedTOC;
    private double[][][] buffer;
    private boolean decoded = false;

    public Frame(Bitreader reader, ImageHeader globalMetadata) {
        this.globalReader = reader;
        this.globalMetadata = globalMetadata;
    }

    public Frame(Frame frame) {
        this(frame, true);
    }

    public Frame(Frame frame, boolean copyBuffer) {
        if (!frame.decoded)
            throw new IllegalArgumentException();
        this.decoded = frame.decoded;
        this.header = new FrameHeader(frame.header);
        this.numGroups = frame.numGroups;
        this.numLFGroups = frame.numLFGroups;
        this.tocPermuation = frame.tocPermuation;
        this.tocLengths = frame.tocLengths;
        this.lfGlobal = frame.lfGlobal;
        this.globalMetadata = frame.globalMetadata;
        this.permutedTOC = frame.permutedTOC;
        this.buffer = new double[frame.buffer.length][][];
        for (int c = 0; c < buffer.length; c++) {
            if (copyBuffer) {
                buffer[c] = new double[frame.buffer[c].length][];
            } else {
                buffer[c] = new double[globalMetadata.getSize().height][];
            }
            for (int y = 0; y < buffer[c].length; y++) {
                if (copyBuffer)
                    buffer[c][y] = Arrays.copyOf(frame.buffer[c][y], frame.buffer[c][y].length);
                else
                    buffer[c][y] = new double[globalMetadata.getSize().width];
            }
        }
        if (!copyBuffer) {
            header.width = globalMetadata.getSize().width;
            header.height = globalMetadata.getSize().height;
            header.origin = new IntPoint();
        }
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
            // if we don't declare a new variable here with the unchecked assignment
            // it won't compile, for some reason
            @SuppressWarnings("unchecked")
            CompletableFuture<byte[]>[] buffers = new CompletableFuture[tocEntries];
            this.buffers = buffers;
        }

        for (int i = 0; i < tocEntries; i++)
            buffers[i] = new CompletableFuture<>();

        for (int i = 0; i < tocEntries; i++)
            tocLengths[i] = globalReader.readU32(0, 10, 1024, 14, 17408, 22, 4211712, 30);

        globalReader.zeroPadToByte();

        if (tocEntries != 1) {
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

    private static int mirrorCoordinate(int coordinate, int size) {
        if (coordinate < 0)
            return mirrorCoordinate(-coordinate - 1, size);
        if (coordinate >= size)
            return mirrorCoordinate(2 * size - coordinate - 1, size);
        return coordinate;
    }

    private void upsample() {
        for (int c = 0; c < buffer.length; c++) {
            performUpsampling(c);
        }
        header.width *= header.upsampling;
        header.height *= header.upsampling;
        header.origin.timesEquals(header.upsampling);
    }

    private void performUpsampling(int c) {
        int color = globalMetadata.getColorChannelCount();
        int k;
        if (c < color)
            k = header.upsampling;
        else
            k = header.ecUpsampling[c - color];
        if (k == 1)
            return;
        int l = MathHelper.ceilLog1p(k - 1) - 1;
        double[][][][] upWeights = globalMetadata.getUpWeights()[l];
        double[][] newBuffer = new double[buffer[c].length * k][];
        TaskList<Void> taskList = new TaskList<>();
        for (int y_ = 0; y_ < buffer[c].length; y_++) {
            final int y = y_;
            taskList.submit(() -> {
                for (int ky = 0; ky < k; ky++) {
                    newBuffer[y*k + ky] = new double[buffer[c][y].length * k];
                    for (int x = 0; x < buffer[c][y].length; x++) {
                        for (int kx = 0; kx < k; kx++) {
                            double[][] weights = upWeights[ky][kx];
                            double total = 0D;
                            double min = Double.MAX_VALUE;
                            double max = Double.MIN_VALUE;
                            for (int iy = 0; iy < 5; iy++) {
                                for (int ix = 0; ix < 5; ix++) {
                                    int newY = mirrorCoordinate(y + iy - 2, buffer[c].length);
                                    int newX = mirrorCoordinate(x + ix - 2, buffer[c][newY].length);
                                    double sample = buffer[c][newY][newX];
                                    if (sample < min)
                                        min = sample;
                                    if (sample > max)
                                        max = sample;
                                    total += weights[iy][ix] * sample;
                                }
                            }
                            newBuffer[y*k + ky][x*k + kx] = MathHelper.clamp(total, min, max);
                        }
                    }
                }
            });
        }
        taskList.collect();
        buffer[c] = newBuffer;
    }

    public void decodeFrame() throws IOException {
        if (this.decoded)
            return;
        this.decoded = true;
        lfGlobal = new LFGlobal(getBitreader(0).join(), this);
        buffer = new double[globalMetadata.getTotalChannelCount()][header.height][header.width];
        List<ModularChannelInfo> lfReplacementChannels = new ArrayList<>();
        List<Integer> lfReplacementChannelIndicies = new ArrayList<>();
        int lfRowStride = MathHelper.ceilDiv(header.width, header.groupDim << 3);
        for (int i = 0; i < lfGlobal.gModular.stream.getEncodedChannelCount(); i++) {
            ModularChannel chan = lfGlobal.gModular.stream.getChannel(i);
            if (!chan.isDecoded()) {
                if (chan.hshift >= 3 && chan.vshift >= 3) {
                    lfReplacementChannelIndicies.add(i);
                    int width = header.groupDim >> (chan.hshift - 3);
                    int height = header.groupDim >> (chan.vshift - 3);
                    lfReplacementChannels.add(new ModularChannelInfo(width, height, chan.hshift, chan.vshift));
                }
            }
        }

        TaskList<Void> tasks = new TaskList<>();
        TaskList<LFGroup> lfGroupTasks = new TaskList<>();

        for (int lfGroupID0 = 0; lfGroupID0 < numLFGroups; lfGroupID0++) {
            final int lfGroupID = lfGroupID0;
            lfGroupTasks.submit(() -> {
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
            });
        }

        LFGroup[] lfGroups = lfGroupTasks.collect().stream().toArray(LFGroup[]::new);

        for (int lfGroupID = 0; lfGroupID < numLFGroups; lfGroupID++) {
            for (int j = 0; j < lfReplacementChannelIndicies.size(); j++) {
                int index = lfReplacementChannelIndicies.get(j);
                ModularChannel channel = lfGlobal.gModular.stream.getChannel(index);
                ModularChannel newChannel = lfGroups[lfGroupID].lfStream.getChannel(j);
                for (int y = 0; y < newChannel.height; y++) {
                    final int y_ = y;
                    tasks.submit(() -> {
                        for (int x = 0; x < newChannel.width; x++) {
                            channel.set(x + newChannel.x0, y_ + newChannel.y0, newChannel.get(x, y_));
                        }
                    });
                }
            }
        }

        if (header.encoding == FrameFlags.VARDCT) {
            throw new UnsupportedOperationException("VarDCT is not yet implemented");
        }

        int numPasses = header.passes.numPasses;
        Pass[] passes = new Pass[numPasses];
        PassGroup[][] passGroups = new PassGroup[numPasses][];

        TaskList<PassGroup> passGroupTasks = new TaskList<>(numPasses);

        for (int pass0 = 0; pass0 < numPasses; pass0++) {
            final int pass = pass0;
            passes[pass] = new Pass(this, pass, pass > 0 ? passes[pass - 1].minShift : 0);
            for (int group0 = 0; group0 < numGroups; group0++) {
                final int group = group0;
                passGroupTasks.submit(pass, () -> {
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
                });
            }
        }

        for (int pass = 0; pass < numPasses; pass++) {
            int[] indices = passes[pass].replacedChannelIndices;
            passGroups[pass] = passGroupTasks.collect(pass).stream().toArray(PassGroup[]::new);
            for (int j = 0; j < indices.length; j++) {
                int index = indices[j];
                ModularChannel channel = lfGlobal.gModular.stream.getChannel(index);
                for (int group = 0; group < numGroups; group++) {
                    ModularChannel newChannel = passGroups[pass][group].stream.getChannel(j);
                    for (int y = 0; y < newChannel.height; y++) {
                        final int y_ = y;
                        tasks.submit(() -> {
                            for (int x = 0; x < newChannel.width; x++) {
                                channel.set(x + newChannel.x0, y_ + newChannel.y0, newChannel.get(x, y_));
                            }
                        });
                    }
                }
            }
        }

        tasks.collect();

        lfGlobal.gModular.stream.applyTransforms();
        int[][][] streamBuffer = lfGlobal.gModular.stream.getDecodedBuffer();

        for (int c_ = 0; c_ < buffer.length; c_++) {
            final int c = c_;
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
            final int cOut_ = cOut;
            for (int y_ = 0; y_ < header.height; y_++) {
                final int y = y_;
                tasks.submit(() -> {
                    for (int x = 0; x < header.width; x++) {
                        if (xyb && c == 2) {
                            buffer[cOut_][y][x] = scaleFactor * (streamBuffer[0][y][x] + streamBuffer[2][y][x]);
                        } else {
                            buffer[cOut_][y][x] = scaleFactor * streamBuffer[c][y][x];
                        }
                    }
                });
            }
        }
        tasks.collect();

        upsample();
    }

    public void renderSplines() {
        if (lfGlobal.splines == null)
            return;
        for (int s = 0; s < lfGlobal.splines.numSplines; s++) {
            Spline spline = new Spline(s, lfGlobal.splines.controlPoints[s]);
            spline.renderSpline(this);
        }
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

    public double[][][] getBuffer() {
        return buffer;
    }

    public boolean isDecoded() {
        return this.decoded;
    }

    /* gets the sample for the IMAGE position x and y */
    public double getSample(int c, int x, int y) {
        return x < header.origin.x || y < header.origin.y
            || x - header.origin.x >= header.width
            || y - header.origin.y >= header.height
            ? 0 : buffer[c][y - header.origin.y][x - header.origin.x];
    }
}
