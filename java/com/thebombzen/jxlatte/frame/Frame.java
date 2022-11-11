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
import com.thebombzen.jxlatte.frame.features.NoiseGroup;
import com.thebombzen.jxlatte.frame.features.Spline;
import com.thebombzen.jxlatte.frame.group.LFGroup;
import com.thebombzen.jxlatte.frame.group.Pass;
import com.thebombzen.jxlatte.frame.group.PassGroup;
import com.thebombzen.jxlatte.frame.modular.ModularChannel;
import com.thebombzen.jxlatte.frame.modular.ModularChannelInfo;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.io.InputStreamBitreader;
import com.thebombzen.jxlatte.util.IntPoint;
import com.thebombzen.jxlatte.util.MathHelper;
import com.thebombzen.jxlatte.util.TaskList;
import com.thebombzen.jxlatte.util.functional.FunctionalHelper;

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
    private double[][][] noiseBuffer;
    private Pass[] passes;
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

    private double[][] performUpsampling(double[][] buffer, int c) {
        int color = globalMetadata.getColorChannelCount();
        int k;
        if (c < color)
            k = header.upsampling;
        else
            k = header.ecUpsampling[c - color];
        if (k == 1)
            return buffer;
        int l = MathHelper.ceilLog1p(k - 1) - 1;
        double[][][][] upWeights = globalMetadata.getUpWeights()[l];
        double[][] newBuffer = new double[buffer.length * k][];
        TaskList<Void> tasks = new TaskList<>();
        for (int y0 = 0; y0 < buffer.length; y0++) {
            tasks.submit(y0, (y) -> {
                for (int ky = 0; ky < k; ky++) {
                    newBuffer[y*k + ky] = new double[buffer[y].length * k];
                    for (int x = 0; x < buffer[y].length; x++) {
                        for (int kx = 0; kx < k; kx++) {
                            double[][] weights = upWeights[ky][kx];
                            double total = 0D;
                            double min = Double.MAX_VALUE;
                            double max = Double.MIN_VALUE;
                            for (int iy = 0; iy < 5; iy++) {
                                for (int ix = 0; ix < 5; ix++) {
                                    int newY = mirrorCoordinate(y + iy - 2, buffer.length);
                                    int newX = mirrorCoordinate(x + ix - 2, buffer[newY].length);
                                    double sample = buffer[newY][newX];
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
        tasks.collect();
        return newBuffer;
    }

    private void decodeLFGroups(TaskList<?> tasks) throws IOException {

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

        TaskList<LFGroup> lfGroupTasks = new TaskList<>();

        for (int lfGroupID0 = 0; lfGroupID0 < numLFGroups; lfGroupID0++) {
            final int lfGroupID = lfGroupID0;
            lfGroupTasks.submit(getBitreader(1 + lfGroupID), (reader) -> {
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
                return new LFGroup(reader, this, lfGroupID, replaced);
            });
        }

        LFGroup[] lfGroups = lfGroupTasks.collect().stream().toArray(LFGroup[]::new);

        /* decode populate LF Groups */
        for (int lfGroupID = 0; lfGroupID < numLFGroups; lfGroupID++) {
            for (int j = 0; j < lfReplacementChannelIndicies.size(); j++) {
                int index = lfReplacementChannelIndicies.get(j);
                ModularChannel channel = lfGlobal.gModular.stream.getChannel(index);
                ModularChannel newChannel = lfGroups[lfGroupID].modularLFGroup.getChannel(j);
                for (int y_ = 0; y_ < newChannel.height; y_++) {
                    tasks.submit(y_, (y) -> {
                        for (int x = 0; x < newChannel.width; x++) {
                            channel.set(x + newChannel.x0, y + newChannel.y0, newChannel.get(x, y));
                        }
                    });
                }
            }
        }
    }

    private void decodeHFGlobal(TaskList<?> tasks) throws IOException {

    }

    private void decodePasses(TaskList<?> tasks) throws IOException {
        passes = new Pass[header.passes.numPasses];
        for (int pass = 0; pass < passes.length; pass++) {
            passes[pass] = new Pass(this, pass, pass > 0 ? passes[pass - 1].minShift : 0);
        }
    }

    private void decodePassGroups(TaskList<?> tasks) throws IOException {

        int numPasses = passes.length;
        PassGroup[][] passGroups = new PassGroup[numPasses][];
        TaskList<PassGroup> passGroupTasks = new TaskList<>(numPasses);

        for (int pass0 = 0; pass0 < numPasses; pass0++) {
            final int pass = pass0;
            for (int group0 = 0; group0 < numGroups; group0++) {
                final int group = group0;
                passGroupTasks.submit(pass, getBitreader(2 + numLFGroups + pass * numGroups + group), (reader) -> {
                    ModularChannelInfo[] replaced = Arrays.asList(passes[pass].replacedChannels)
                        .stream().map(ModularChannelInfo::new).toArray(ModularChannelInfo[]::new);
                    for (ModularChannelInfo info : replaced) {
                        int passGroupWidth = header.groupDim >> info.hshift;
                        int passGroupHeight = header.groupDim >> info.vshift;
                        int rowStride = MathHelper.ceilDiv(info.width, passGroupWidth);
                        info.x0 = (group % rowStride) * passGroupWidth;
                        info.y0 = (group / rowStride) * passGroupHeight;
                        info.width = Math.min(passGroupWidth, info.width - info.x0);
                        info.height = Math.min(passGroupHeight, info.height - info.y0);
                    }
                    return new PassGroup(reader, Frame.this,
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
                    for (int y_ = 0; y_ < newChannel.height; y_++) {
                        tasks.submit(y_, (y) -> {
                            for (int x = 0; x < newChannel.width; x++) {
                                channel.set(x + newChannel.x0, y + newChannel.y0, newChannel.get(x, y));
                            }
                        });
                    }
                }
            }
        }

        tasks.collect();
    }

    public void decodeFrame() throws IOException {
        if (this.decoded)
            return;
        this.decoded = true;
        lfGlobal = FunctionalHelper.join(getBitreader(0).thenApplyAsync(
            FunctionalHelper.uncheck((reader) -> new LFGlobal(reader, this))));
        buffer = new double[globalMetadata.getTotalChannelCount()][header.height][header.width];
        TaskList<Void> tasks = new TaskList<>();

        decodeLFGroups(tasks);

        if (header.encoding == FrameFlags.VARDCT) {
            decodeHFGlobal(tasks);
            throw new UnsupportedOperationException("VarDCT is not yet implemented");
        }

        decodePasses(tasks);
        decodePassGroups(tasks);

        lfGlobal.gModular.stream.applyTransforms();
        int[][][] streamBuffer = lfGlobal.gModular.stream.getDecodedBuffer();

        for (int c = 0; c < buffer.length; c++) {
            double scaleFactor;
            boolean xyb = globalMetadata.isXYBEncoded();
            // X, Y, B is encoded as Y, X, (B - Y)
            int cOut = xyb && c < 2 ? 1 - c : c;
            if (xyb)
                scaleFactor = lfGlobal.lfDequant[cOut];
            else if (globalMetadata.getBitDepthHeader().expBits != 0)
                scaleFactor = 1.0D;
            else
                scaleFactor = 1.0D / ~(~0L << globalMetadata.getBitDepthHeader().bitsPerSample);
            for (int y_ = 0; y_ < header.height; y_++) {
                tasks.submit(y_, c, (y, c2) -> {
                    for (int x = 0; x < header.width; x++) {
                        // X, Y, B is encoded as Y, X, (B - Y)
                        if (xyb && c2 == 2)
                            buffer[cOut][y][x] = scaleFactor * (streamBuffer[0][y][x] + streamBuffer[2][y][x]);
                        else
                            buffer[cOut][y][x] = scaleFactor * streamBuffer[c2][y][x];
                    }
                });
            }
        }
        tasks.collect();
    }

    public void upsample() {
        for (int c = 0; c < buffer.length; c++)
            buffer[c] = performUpsampling(buffer[c], c);
        header.width *= header.upsampling;
        header.height *= header.upsampling;
        header.origin.timesEquals(header.upsampling);
    }

    public void renderSplines() {
        if (lfGlobal.splines == null)
            return;
        for (int s = 0; s < lfGlobal.splines.numSplines; s++) {
            Spline spline = new Spline(s, lfGlobal.splines.controlPoints[s]);
            spline.renderSpline(this);
        }
    }

    public void initializeNoise(long seed0) {
        if (lfGlobal.noiseParameters == null)
            return;
        int rowStride = MathHelper.ceilDiv(header.width, header.groupDim);
        int height = header.height * header.upsampling;
        int width = header.width * header.upsampling;
        double[][][] noiseBuffer = new double[3][height][width];
        int numGroups = rowStride * MathHelper.ceilDiv(header.height, header.groupDim);
        TaskList<Void> tasks = new TaskList<>();
        for (int group = 0; group < numGroups; group++) {
            int groupX = group % rowStride;
            int groupY = group / rowStride;
            // SPEC: spec doesn't mention how noise groups interact with upsampling
            for (int iy = 0; iy < header.upsampling; iy++) {
                for (int ix = 0; ix < header.upsampling; ix++) {
                    int x0 = (groupX * header.upsampling + ix) * header.groupDim;
                    int y0 = (groupY * header.upsampling + iy) * header.groupDim;
                    tasks.submit(() -> new NoiseGroup(header, seed0, noiseBuffer, x0, y0));
                }
            }
        }
        double[][] laplacian = new double[5][5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                laplacian[i][j] = (i == 2 && j == 2 ? -3.84D : 0.16D);
            }
        }
        this.noiseBuffer = new double[3][height][width];
        tasks.collect();
        for (int c_ = 0; c_ < 3; c_++) {
            for (int y_ = 0; y_ < height; y_++) {
                tasks.submit(c_, y_, (c, y) -> {
                    for (int x = 0; x < width; x++) {
                        for (int iy = 0; iy < 5; iy++) {
                            for (int ix = 0; ix < 5; ix++) {
                                int cy = mirrorCoordinate(y + iy - 2, height);
                                int cx = mirrorCoordinate(x + ix - 2, width);
                                this.noiseBuffer[c][y][x] += noiseBuffer[c][cy][cx] * laplacian[iy][ix];
                            }
                        }
                    }
                });
            }
        }
        tasks.collect();
    }

    public void synthesizeNoise() {
        if (lfGlobal.noiseParameters == null)
            return;
        double[] lut = lfGlobal.noiseParameters.lut;
        for (int y = 0; y < header.height; y++) {
            for (int x = 0; x < header.width; x++) {
                // SPEC: spec doesn't mention the *0.5 here, it says *6
                // SPEC: spec doesn't mention clamping to 0 here
                double inScaledR = 3D * Math.max(0D, buffer[1][y][x] + buffer[0][y][x]);
                double inScaledG = 3D * Math.max(0D, buffer[1][y][x] - buffer[0][y][x]);
                int intInR;
                double fracInR;
                // LIBJXL: libjxl bug makes this >= 6D and 5, making lut[7] unused
                // SPEC: spec bug makes this >= 8D and 7, making lut[8] overflow
                if (inScaledR >= 7D) {
                    intInR = 6;
                    fracInR = 1D;
                } else {
                    intInR = (int)inScaledR;
                    fracInR = inScaledR - intInR;
                }
                int intInG;
                double fracInG;
                if (inScaledG >= 7D) {
                    intInG = 6;
                    fracInG = 1D;
                } else {
                    intInG = (int)inScaledG;
                    fracInG = inScaledG - intInG;
                }
                double sr = (lut[intInR + 1] - lut[intInR]) * fracInR + lut[intInR];
                double sg = (lut[intInG + 1] - lut[intInG]) * fracInG + lut[intInG];
                double nr = 0.22D * sr * (0.0078125D * noiseBuffer[0][y][x] + 0.9921875D * noiseBuffer[2][y][x]);
                double ng = 0.22D * sg * (0.0078125D * noiseBuffer[1][y][x] + 0.9921875D * noiseBuffer[2][y][x]);
                double nrg = nr + ng;
                buffer[0][y][x] += lfGlobal.lfChanCorr.baseCorrelationX * nrg + nr - ng;
                buffer[1][y][x] += nrg;
                buffer[2][y][x] += lfGlobal.lfChanCorr.baseCorrelationB * nrg;
            }
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

    public boolean isVisible() {
        return (header.type == FrameFlags.REGULAR_FRAME || header.type == FrameFlags.SKIP_PROGRESSIVE)
            && (header.duration != 0 || header.isLast);
    }

    /* gets the sample for the IMAGE position x and y */
    public double getSample(int c, int x, int y) {
        return x < header.origin.x || y < header.origin.y
            || x - header.origin.x >= header.width
            || y - header.origin.y >= header.height
            ? 0 : buffer[c][y - header.origin.y][x - header.origin.x];
    }
}
