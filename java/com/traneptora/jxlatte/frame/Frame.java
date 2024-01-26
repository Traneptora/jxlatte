package com.traneptora.jxlatte.frame;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntUnaryOperator;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.JXLOptions;
import com.traneptora.jxlatte.bundle.ImageHeader;
import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.frame.features.noise.NoiseGroup;
import com.traneptora.jxlatte.frame.features.spline.Spline;
import com.traneptora.jxlatte.frame.group.LFGroup;
import com.traneptora.jxlatte.frame.group.Pass;
import com.traneptora.jxlatte.frame.group.PassGroup;
import com.traneptora.jxlatte.frame.modular.MATree;
import com.traneptora.jxlatte.frame.modular.ModularChannel;
import com.traneptora.jxlatte.frame.modular.ModularChannelInfo;
import com.traneptora.jxlatte.frame.vardct.HFGlobal;
import com.traneptora.jxlatte.frame.vardct.HFPass;
import com.traneptora.jxlatte.frame.vardct.TransformType;
import com.traneptora.jxlatte.frame.vardct.Varblock;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.Loggers;
import com.traneptora.jxlatte.util.FlowHelper;
import com.traneptora.jxlatte.util.IntPoint;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.TaskList;
import com.traneptora.jxlatte.util.functional.ExceptionalFunction;
import com.traneptora.jxlatte.util.functional.FunctionalHelper;

public class Frame {

    public static final int[] cMap = new int[]{1, 0, 2};

    private static IntPoint[] epfCross = new IntPoint[] {
        IntPoint.ZERO,
        new IntPoint(0, 1), new IntPoint(1, 0),
        new IntPoint(-1, 0), new IntPoint(0, -1),
    };

    private static IntPoint[] epfDoubleCross = new IntPoint[] {
        IntPoint.ZERO,
        new IntPoint(-1, 0), new IntPoint(0, -1), new IntPoint(1, 0), new IntPoint(0, 1),
        new IntPoint(1, -1), new IntPoint(1, 1), new IntPoint(-1, -1), new IntPoint(-1, 1),
        new IntPoint(2, 0), new IntPoint(0, 2), new IntPoint(-2, 0), new IntPoint(-2, 2),
    };

    private static float[][] laplacian = null;

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
    private float[][][] buffer;
    private float[][][] noiseBuffer;
    private Pass[] passes;
    private boolean decoded = false;
    private HFGlobal hfGlobal;
    private LFGroup[] lfGroups;
    private int groupRowStride;
    private int lfGroupRowStride;
    private MATree globalTree;
    private int width;
    private int height;
    private Loggers loggers;
    private FlowHelper flowHelper;
    private JXLOptions options;

    public Frame(Bitreader reader, ImageHeader globalMetadata, FlowHelper flowHelper,
            Loggers loggers, JXLOptions options) {
        this.globalReader = reader;
        this.globalMetadata = globalMetadata;
        this.loggers = loggers;
        this.flowHelper = flowHelper;
        this.options = options;
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
        this.buffer = new float[frame.buffer.length][][];
        for (int c = 0; c < buffer.length; c++) {
            if (copyBuffer) {
                buffer[c] = new float[frame.buffer[c].length][];
            } else {
                buffer[c] = new float[globalMetadata.getSize().height][];
            }
            for (int y = 0; y < buffer[c].length; y++) {
                if (copyBuffer)
                    buffer[c][y] = Arrays.copyOf(frame.buffer[c][y], frame.buffer[c][y].length);
                else
                    buffer[c][y] = new float[globalMetadata.getSize().width];
            }
        }
        if (!copyBuffer) {
            header.width = globalMetadata.getSize().width;
            header.height = globalMetadata.getSize().height;
            header.origin = new IntPoint();
        }
        this.loggers = frame.loggers;
        this.flowHelper = frame.flowHelper;
        this.options = frame.options;
    }

    public FlowHelper getFlowHelper() {
        return flowHelper;
    }

    private void readHeader() throws IOException {
        globalReader.zeroPadToByte();
        this.header = new FrameHeader(globalReader, this.globalMetadata);
        width = header.width;
        height = header.height;
        width = MathHelper.ceilDiv(width, header.upsampling);
        height = MathHelper.ceilDiv(height, header.upsampling);
        width = MathHelper.ceilDiv(width, 1 << (3 * header.lfLevel));
        height = MathHelper.ceilDiv(height, 1 << (3 * header.lfLevel));
        groupRowStride = MathHelper.ceilDiv(width, header.groupDim);
        lfGroupRowStride = MathHelper.ceilDiv(width, header.groupDim << 3);
        numGroups = groupRowStride * MathHelper.ceilDiv(height, header.groupDim);
        numLFGroups = lfGroupRowStride * MathHelper.ceilDiv(height, header.groupDim << 3);
        readTOC();
    }

    public FrameHeader readFrameHeader() throws IOException {
        readHeader();
        return header;
    }

    public FrameHeader getFrameHeader() {
        return header;
    }

    public void skipFrameData() throws IOException {
        for (int i = 0; i < tocLengths.length; i++) {
            globalReader.skipBits(tocLengths[i] << 3);
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
            EntropyStream tocStream = new EntropyStream(loggers, globalReader, 8);
            tocPermuation = readPermutation(globalReader, tocStream, tocEntries, 0);
            if (!tocStream.validateFinalState())
                throw new InvalidBitstreamException("Invalid final ANS state decoding TOC");
        } else {
            tocPermuation = new int[tocEntries];
            for (int i = 0; i < tocEntries; i++)
                tocPermuation[i] = i;
        }
        loggers.log(Loggers.LOG_TRACE, "Permuted TOC: %b", permutedTOC);
        if (permutedTOC) {
            loggers.log(Loggers.LOG_TRACE, "TOC Permutation: %s", tocPermuation);
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

        loggers.log(Loggers.LOG_TRACE, "TOC Lengths: %s", tocLengths);

        if (tocEntries != 1 && !options.parseOnly) {
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
        byte[] buffer = new byte[length + 4];
        int read = globalReader.read(buffer, 0, length);
        if (read < length)
            throw new EOFException("Unable to read full TOC entry");
        return buffer;
    }

    private CompletableFuture<Bitreader> getBitreader(int index) throws IOException {
        if (tocLengths.length == 1)
            return CompletableFuture.completedFuture(this.globalReader);
        int permutedIndex = tocPermuation[index];
        return buffers[permutedIndex].thenApply((buff) -> {
            return new Bitreader(new ByteArrayInputStream(buff));
        });
    }

    public static int[] readPermutation(Bitreader reader, EntropyStream stream, int size, int skip) throws IOException {
        IntUnaryOperator ctx = x -> Math.min(7, MathHelper.ceilLog1p(x));
        int end = stream.readSymbol(reader, ctx.applyAsInt(size));
        if (end > size - skip)
            throw new InvalidBitstreamException("Illegal end value in lehmer sequence");
        int[] lehmer = new int[size];
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

    private float[][] performUpsampling(float[][] buffer, int c) {
        int color = getColorChannelCount();
        int k;
        if (c < color)
            k = header.upsampling;
        else
            k = header.ecUpsampling[c - color];
        if (k == 1)
            return buffer;
        int l = MathHelper.ceilLog1p(k - 1) - 1;
        float[][][][] upWeights = globalMetadata.getUpWeights()[l];
        float[][] newBuffer = new float[buffer.length * k][];
        TaskList<Void> tasks = new TaskList<>(flowHelper.getThreadPool());
        for (int y0 = 0; y0 < buffer.length; y0++) {
            tasks.submit(y0, (y) -> {
                for (int ky = 0; ky < k; ky++) {
                    newBuffer[y*k + ky] = new float[buffer[y].length * k];
                    for (int x = 0; x < buffer[y].length; x++) {
                        for (int kx = 0; kx < k; kx++) {
                            float[][] weights = upWeights[ky][kx];
                            float total = 0f;
                            float min = Float.MAX_VALUE;
                            float max = Float.MIN_VALUE;
                            for (int iy = 0; iy < 5; iy++) {
                                for (int ix = 0; ix < 5; ix++) {
                                    int newY = MathHelper.mirrorCoordinate(y + iy - 2, buffer.length);
                                    int newX = MathHelper.mirrorCoordinate(x + ix - 2, buffer[newY].length);
                                    float sample = buffer[newY][newX];
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

    private void decodeLFGroups(float[][][] lfBuffer) throws IOException {

        List<ModularChannelInfo> lfReplacementChannels = new ArrayList<>();
        List<Integer> lfReplacementChannelIndicies = new ArrayList<>();
        for (int i = 0; i < lfGlobal.gModular.stream.getEncodedChannelCount(); i++) {
            ModularChannel chan = lfGlobal.gModular.stream.getChannel(i);
            if (!chan.isDecoded()) {
                if (chan.hshift >= 3 && chan.vshift >= 3) {
                    lfReplacementChannelIndicies.add(i);
                    IntPoint size = new IntPoint(header.lfGroupDim).shiftRight(chan.hshift, chan.vshift);
                    lfReplacementChannels.add(new ModularChannelInfo(size.x, size.y, chan.hshift, chan.vshift));
                }
            }
        }

        lfGroups = new LFGroup[numLFGroups];

        for (int lfGroupID = 0; lfGroupID < numLFGroups; lfGroupID++) {
            Bitreader reader = FunctionalHelper.join(getBitreader(1 + lfGroupID));
            IntPoint lfGroupPos = IntPoint.coordinates(lfGroupID, lfGroupRowStride);
            ModularChannelInfo[] replaced = lfReplacementChannels.stream().map(ModularChannelInfo::new)
                .toArray(ModularChannelInfo[]::new);
            IntPoint frameSize = getPaddedFrameSize();
            for (ModularChannelInfo info : replaced) {
                IntPoint shift = new IntPoint(info.hshift, info.vshift);
                IntPoint lfSize = frameSize.shiftRight(shift);
                IntPoint chanSize = new IntPoint(info.width, info.height);
                IntPoint pos = lfGroupPos.times(chanSize);
                IntPoint size = chanSize.min(lfSize.minus(pos));
                info.width = size.x;
                info.height = size.y;
                info.origin = pos;
            }
            lfGroups[lfGroupID] = new LFGroup(reader, this, lfGroupID, replaced, lfBuffer);
        }

        /* populate decoded LF Groups */
        for (int lfGroupID = 0; lfGroupID < numLFGroups; lfGroupID++) {
            for (int j = 0; j < lfReplacementChannelIndicies.size(); j++) {
                int index = lfReplacementChannelIndicies.get(j);
                ModularChannel channel = lfGlobal.gModular.stream.getChannel(index);
                int[][] newChannel = lfGroups[lfGroupID].modularLFGroupBuffer[j];
                ModularChannelInfo newChannelInfo = lfGroups[lfGroupID].modularLFGroupInfo[j];
                for (int y = 0; y < newChannel.length; y++) {
                    System.arraycopy(newChannel[y], 0, channel.buffer[y + newChannelInfo.origin.y],
                        newChannelInfo.origin.x, newChannel[y].length);
                }
            }
        }
    }

    private void decodePasses(Bitreader reader) throws IOException {
        passes = new Pass[header.passes.numPasses];
        for (int pass = 0; pass < passes.length; pass++) {
            passes[pass] = new Pass(reader, this, pass, pass > 0 ? passes[pass - 1].minShift : 0);
        }
    }

    private void decodePassGroups() throws IOException {

        int numPasses = passes.length;
        PassGroup[][] passGroups = new PassGroup[numPasses][];

        TaskList<PassGroup> taskList = new TaskList<>(flowHelper.getThreadPool(), numPasses);
        for (final int pass : FlowHelper.range(numPasses)) {
            for (final int group : FlowHelper.range(numGroups)) {
                taskList.submit(pass, getBitreader(2 + numLFGroups + pass * numGroups + group), (reader) -> {
                    ModularChannelInfo[] replaced = Arrays.asList(passes[pass].replacedChannels).stream()
                    .filter(Objects::nonNull).map(ModularChannelInfo::new).toArray(ModularChannelInfo[]::new);
                    for (ModularChannelInfo info : replaced) {
                        IntPoint shift = new IntPoint(info.hshift, info.vshift);
                        IntPoint passGroupSize = new IntPoint(header.groupDim).shiftRight(shift);
                        int rowStride = MathHelper.ceilDiv(info.width, passGroupSize.x);
                        IntPoint pos = IntPoint.coordinates(group, rowStride).times(passGroupSize);
                        IntPoint chanSize = new IntPoint(info.width, info.height);
                        info.origin = pos;
                        IntPoint size = passGroupSize.min(chanSize.minus(info.origin));
                        info.width = size.x;
                        info.height = size.y;
                    }
                    return new PassGroup(reader, Frame.this, pass, group, replaced);
                });
            }
        }

        for (int pass = 0; pass < numPasses; pass++) {
            passGroups[pass] = taskList.collect(pass).stream().toArray(PassGroup[]::new);
            int j = 0;
            for (int i = 0; i < passes[pass].replacedChannels.length; i++) {
                if (passes[pass].replacedChannels[i] == null)
                    continue;
                ModularChannel channel = lfGlobal.gModular.stream.getChannel(i);
                for (int group = 0; group < numGroups; group++) {
                    ModularChannelInfo newChannelInfo = passGroups[pass][group].modularPassGroupInfo[j];
                    int[][] buff = passGroups[pass][group].modularPassGroupBuffer[j];
                    for (int y = 0; y < newChannelInfo.height; y++) {
                        System.arraycopy(buff[y], 0, channel.buffer[y + newChannelInfo.origin.y],
                            newChannelInfo.origin.x, newChannelInfo.width);
                    }
                }
                j++;
            }
        }

        if (header.encoding == FrameFlags.VARDCT) {
            TaskList<Void> varDCTTaskList = new TaskList<>(flowHelper.getThreadPool(), numPasses);
            for (final int pass : FlowHelper.range(numPasses)) {
                for (final int group : FlowHelper.range(numGroups)) {
                    varDCTTaskList.submit(pass, () -> {
                        PassGroup passGroup = passGroups[pass][group];
                        PassGroup prev = pass > 0 ? passGroups[pass - 1][group] : null;
                        passGroup.invertVarDCT(buffer, prev);
                    });
                }
                varDCTTaskList.collect(pass);
            }
        }
    }

    public void decodeFrame(float[][][] lfBuffer) throws IOException {
        if (this.decoded)
            return;
        this.decoded = true;

        lfGlobal = FunctionalHelper.join(getBitreader(0)
            .thenApplyAsync(ExceptionalFunction.of((reader) -> new LFGlobal(reader, this))));
        IntPoint paddedSize = getPaddedFrameSize();

        buffer = new float[getColorChannelCount() + globalMetadata.getExtraChannelCount()][][];
        for (int c = 0; c < buffer.length; c++) {
            if (c < 3 && c < getColorChannelCount()) {
                IntPoint shiftedSize = paddedSize.shiftRight(header.jpegUpsampling[c]);
                buffer[c] = new float[shiftedSize.y][shiftedSize.x];
            } else {
                buffer[c] = new float[paddedSize.y][paddedSize.x];
            }
        }

        decodeLFGroups(lfBuffer);

        Bitreader hfGlobalReader = FunctionalHelper.join(getBitreader(1 + numLFGroups));
               
        if (header.encoding == FrameFlags.VARDCT)
            hfGlobal = new HFGlobal(hfGlobalReader, this);
        else
            hfGlobal = null;

        decodePasses(hfGlobalReader);

        decodePassGroups();

        lfGlobal.gModular.stream.applyTransforms();
        int[][][] modularBuffer = lfGlobal.gModular.stream.getDecodedBuffer();

        for (int c = 0; c < modularBuffer.length; c++) {
            int cIn = c;
            float scaleFactor;
            boolean isModularColor = header.encoding == FrameFlags.MODULAR && c < getColorChannelCount();
            boolean isModularXYB = globalMetadata.isXYBEncoded() && isModularColor;
            // X, Y, B is encoded as Y, X, (B - Y)
            int cOut = (isModularXYB ? cMap[c] : c) + buffer.length - modularBuffer.length;
            int ecIndex = c - (header.encoding == FrameFlags.MODULAR ? globalMetadata.getColorChannelCount() : 0);
            if (isModularXYB)
                scaleFactor = lfGlobal.lfDequant[cOut];
            else if (isModularColor && globalMetadata.getBitDepthHeader().expBits != 0)
                scaleFactor = 1.0f;
            else if (isModularColor)
                scaleFactor = 1.0f / ~(~0L << globalMetadata.getBitDepthHeader().bitsPerSample);
            else if (globalMetadata.getExtraChannelInfo(ecIndex).bitDepth.expBits != 0)
                scaleFactor = 1.0f;
            else
                scaleFactor = 1.0f / ~(~0L << globalMetadata.getExtraChannelInfo(ecIndex).bitDepth.bitsPerSample);
            if (isModularXYB && cIn == 2) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++)
                        buffer[cOut][y][x] = scaleFactor * (modularBuffer[0][y][x] + modularBuffer[2][y][x]);
                }
            } else {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++)
                        buffer[cOut][y][x] = scaleFactor * modularBuffer[cIn][y][x];
                }
            }
        }

        invertSubsampling();

        if (header.restorationFilter.gab)
            performGabConvolution();

        if (header.restorationFilter.epfIterations > 0)
            performEdgePreservingFilter();
    }

    // do this in RGB
    public void drawVarblocks() {
        for (LFGroup lfg : lfGroups) {
            IntPoint pixelPosInFrame = getLFGroupXY(lfg.lfGroupID).shiftLeft(11);
            for (int i = 0; i < lfg.hfMetadata.blockList.length; i++) {
                Varblock varblock = lfg.hfMetadata.getVarblock(i);
                TransformType tt = varblock.transformType();
                float hue = (((float)tt.type * MathHelper.PHI_BAR) % 1.0f) * 2f * (float)Math.PI;
                float rFactor = ((float)Math.cos(hue) + 0.5f) / 1.5f;
                float gFactor = ((float)Math.cos(hue - 2f * (float)Math.PI / 3f) + 1f) / 2f;
                float bFactor = ((float)Math.cos(hue - 4f * (float)Math.PI / 3f) + 1f) / 2f;
                IntPoint corner = varblock.pixelPosInLFGroup.plus(pixelPosInFrame);
                IntPoint size = varblock.sizeInPixels();
                for (int y = 0; y < size.y; y++) {
                    for (int x = 0; x < size.x; x++) {
                        float sampleR = buffer[0][y + corner.y][x + corner.x];
                        float sampleG = buffer[1][y + corner.y][x + corner.x];
                        float sampleB = buffer[2][y + corner.y][x + corner.x];
                        if (x == 0 || y == 0) {
                            buffer[1][y + corner.y][x + corner.x] = 0f;
                            buffer[0][y + corner.y][x + corner.x] = 0f;
                            buffer[2][y + corner.y][x + corner.x] = 0f;
                        } else {
                            float light = 0.25f * (sampleR + sampleB) + 0.5f * sampleG;
                            light = 0.5f * ((float)Math.cbrt(light) - 0.5f) + 0.5f;
                            buffer[0][y + corner.y][x + corner.x] = rFactor * 0.5f + 0.5f * sampleR / light;
                            buffer[1][y + corner.y][x + corner.x] = gFactor * 0.5f + 0.5f * sampleG / light;
                            buffer[2][y + corner.y][x + corner.x] = bFactor * 0.5f + 0.5f * sampleB / light;
                        }
                    }
                }
            }
        }
    }

    private void performGabConvolution() {
        final TaskList<?> tasks = new TaskList<>(flowHelper.getThreadPool(), 3);
        final float[][] normGabW = new float[3][getColorChannelCount()];
        for (int c = 0; c < getColorChannelCount(); c++) {
            final float gabW1 = header.restorationFilter.gab1Weights[c];
            final float gabW2 = header.restorationFilter.gab2Weights[c];
            final float mult = 1f / (1f + 4f * (gabW1 + gabW2));
            normGabW[0][c] = mult;
            normGabW[1][c] = gabW1 * mult;
            normGabW[2][c] = gabW2 * mult;
        }
        for (final int c : FlowHelper.range(getColorChannelCount())) {
            final float[][] buffC = buffer[c];
            final int height = buffC.length;
            final int width = buffC[0].length;
            final float[][] newBuffer = new float[height][width];
            for (final int y : FlowHelper.range(height)) {
                final int north = MathHelper.mirrorCoordinate(y - 1, height);
                final int south = MathHelper.mirrorCoordinate(y + 1, height);
                final float[] buffR = buffC[y];
                final float[] buffN = buffC[north];
                final float[] buffS = buffC[south];
                final float[] newBuffR = newBuffer[y];
                tasks.submit(c, () -> {
                    for (int x = 0; x < width; x++) {
                        final int west = MathHelper.mirrorCoordinate(x - 1, width);
                        final int east = MathHelper.mirrorCoordinate(x + 1, width);
                        final float adj = buffR[west] + buffR[east] + buffN[x] + buffS[x];
                        final float diag = buffN[west] + buffN[east] + buffS[west] + buffS[east];
                        newBuffR[x] = normGabW[0][c] * buffR[x] + normGabW[1][c] * adj + normGabW[2][c] * diag;
                    }
                });
            }
            tasks.collect(c);
            buffer[c] = newBuffer;
        }
    }

    private void performEdgePreservingFilter() throws InvalidBitstreamException {
        final float stepMultiplier = (float)(1.65D * 4D * (1D - MathHelper.SQRT_H));

        final IntPoint size = getPaddedFrameSize();
        final int blockW = MathHelper.ceilDiv(size.x, 8);
        final int blockH = MathHelper.ceilDiv(size.y, 8);
        final float[][] inverseSigma = new float[blockH][blockW];
        final int dimS = header.logLFGroupDim - 3;

        CompletableFuture<?>[] futures = new CompletableFuture[size.y];

        if (header.encoding == FrameFlags.MODULAR) {
            final float inv = 1f / header.restorationFilter.epfSigmaForModular;
            for (int y = 0; y < blockH; y++)
                Arrays.fill(inverseSigma[y], inv);
        } else {
            for (int y0 = 0; y0 < blockH; y0++) {
                final int y = y0;
                final float[] invSY = inverseSigma[y];
                final int lfY = y >> dimS;
                final int bY = y - (lfY << dimS);
                final int lfR = lfY * lfGroupRowStride;
                futures[y] = CompletableFuture.runAsync(() -> {
                    for (int x = 0; x < blockW; x++) {
                        final int lfX = x >> dimS;
                        final int bX = x - (lfX << dimS);
                        final LFGroup lfg = lfGroups[lfR + lfX];
                        final int hf = lfg.hfMetadata.blockMap[bY][bX].hfMult();
                        final int sharpness = lfg.hfMetadata.hfStreamBuffer[3][bY][bX];
                        if (sharpness < 0 || sharpness > 7)
                            FunctionalHelper.sneakyThrow(new InvalidBitstreamException("Invalid EPF Sharpness: " + sharpness));
                        final float sigma = hf * header.restorationFilter.epfSharpLut[sharpness];
                        invSY[x] = 1f / sigma;
                    }
                });
            }
            for (int y = 0; y < blockH; y++)
                futures[y].join();
        }

        final float[][][] outputBuffer = new float[getColorChannelCount()][size.y][size.x];

        for (final int i : FlowHelper.range(3)) {
            if (i == 0 && header.restorationFilter.epfIterations < 3)
                continue;
            if (i == 2 && header.restorationFilter.epfIterations < 2)
                break;
            final float sigmaScale = stepMultiplier * (i == 0 ? header.restorationFilter.epfPass0SigmaScale :
                i == 1 ? 1.0f : header.restorationFilter.epfPass2SigmaScale);
            final IntPoint[] pc = i == 0 ? epfDoubleCross : epfCross;
            for (final int y : FlowHelper.range(size.y)) {
                final float[] invSY = inverseSigma[y >> 3];
                futures[y] = CompletableFuture.runAsync(() -> {
                    for (int x = 0; x < size.x; x++) {
                        float sumWeights = 0f;
                        final float[] sumChannels = new float[outputBuffer.length];
                        final float s = invSY[x >> 3];
                        if (s != s || s > 3.333333333f) {
                            for (int c = 0; c < outputBuffer.length; c++)
                                outputBuffer[c][y][x] = buffer[c][y][x];
                            continue;
                        }
                        for (int j = 0; j < pc.length; j++) {
                            final IntPoint ip = pc[j];
                            final int nX = x + ip.x;
                            final int nY = y + ip.y;
                            final int mX = MathHelper.mirrorCoordinate(nX, size.x);
                            final int mY = MathHelper.mirrorCoordinate(nY, size.y);
                            final float dist = i == 2 ? epfDistance2(buffer, outputBuffer.length, x, y, nX, nY, size)
                                : epfDistance1(buffer, outputBuffer.length, x, y, nX, nY, size);
                            final float weight = epfWeight(sigmaScale, dist, s, x, y);
                            sumWeights += weight;
                            for (int c = 0; c < outputBuffer.length; c++)
                                sumChannels[c] += buffer[c][mY][mX] * weight;
                        }
                        for (int c = 0; c < outputBuffer.length; c++)
                            outputBuffer[c][y][x] = sumChannels[c] / sumWeights;
                    }
                });
            }
            for (int y = 0; y < size.y; y++)
                futures[y].join();
            for (int c = 0; c < outputBuffer.length; c++) {
                /* swapping lets us re-use the output buffer without re-allocing */
                final float[][] tmp = buffer[c];
                buffer[c] = outputBuffer[c];
                outputBuffer[c] = tmp;
            }
        }
    }

    private float epfDistance1(final float[][][] buffer, final int colors, final int basePosX, final int basePosY,
            final int distPosX, final int distPosY, final IntPoint size) {
        float dist = 0;
        for (int c = 0; c < colors; c++) {
            final float[][] buffC = buffer[c];
            final float scale = header.restorationFilter.epfChannelScale[c];
            for (int i = 0; i < epfCross.length; i++) {
                final IntPoint p = epfCross[i];
                final int pX = MathHelper.mirrorCoordinate(basePosX + p.x, size.x);
                final int pY = MathHelper.mirrorCoordinate(basePosY + p.y, size.y);
                final int dX = MathHelper.mirrorCoordinate(distPosX + p.x, size.x);
                final int dY = MathHelper.mirrorCoordinate(distPosY + p.y, size.y);
                dist += Math.abs(buffC[pY][pX] - buffC[dY][dX]) * scale;
            }
        }

        return dist;
    }

    private float epfDistance2(final float[][][] buffer, final int colors, final int basePosX, final int basePosY,
    final int distPosX, final int distPosY, final IntPoint size) {
        float dist = 0;
        for (int c = 0; c < colors; c++) {
            final float[][] buffC = buffer[c];
            final int dX = MathHelper.mirrorCoordinate(distPosX, size.x);
            final int dY = MathHelper.mirrorCoordinate(distPosY, size.y);
            dist += Math.abs(buffC[basePosY][basePosX] - buffC[dY][dX]) * header.restorationFilter.epfChannelScale[c];
        }
        return dist;
    }

    private float epfWeight(final float stepMultiplier, final float distance,
            final float inverseSigma, final int refX, final int refY) {
        final int modX = refX & 0b111;
        final int modY = refY & 0b111;
        final float dist;
        if (modX == 0 || modX == 7 || modY == 0 || modY == 7)
            dist = distance * header.restorationFilter.epfBorderSadMul;
        else
            dist = distance;
        final float v = 1f - dist * stepMultiplier * inverseSigma;
        if (v <= 0)
            return 0f;

        return v;
    }

    private void invertSubsampling() {
        for (int c = 0; c < 3; c++) {
            int xShift = header.jpegUpsampling[c].x;
            int yShift = header.jpegUpsampling[c].y;
            while (xShift-- > 0) {
                final float[][] oldChannel = buffer[c];
                final float[][] newChannel = new float[oldChannel.length][];
                for (int y = 0; y < oldChannel.length; y++) {
                    final float[] oldRow = oldChannel[y];
                    final float[] newRow = new float[oldRow.length * 2];
                    for (int x = 0; x < oldRow.length; x++) {
                        final float b75 = 0.75f * oldRow[x];
                        newRow[2*x] = b75 + 0.25f * oldRow[x == 0 ? 0 : x - 1];
                        newRow[2*x + 1] = b75 + 0.25f * oldRow[x + 1 == oldRow.length ? oldRow.length - 1 : x + 1];
                    }
                    newChannel[y] = newRow;
                }
                buffer[c] = newChannel;
            }
            while (yShift-- > 0) {
                final float[][] oldChannel = buffer[c];
                final float[][] newChannel = new float[oldChannel.length * 2][];
                for (int y = 0; y < oldChannel.length; y++) {
                    final float[] oldRow = oldChannel[y];
                    final float[] oldRowPrev = oldChannel[y == 0 ? 0 : y - 1];
                    final float[] oldRowNext = oldChannel[y + 1 == oldChannel.length ? oldChannel.length - 1 : y + 1];
                    final float[] firstNewRow = new float[oldRow.length];
                    final float[] secondNewRow = new float[oldRow.length];
                    for (int x = 0; x < oldRow.length; x++) {
                        final float b75 = 0.75f * oldRow[x];
                        firstNewRow[x] = b75 + 0.25f * oldRowPrev[x];
                        secondNewRow[x] = b75 + 0.25f * oldRowNext[x];
                    }
                    newChannel[2*y] = firstNewRow;
                    newChannel[2*y+1] = secondNewRow;
                }
                buffer[c] = newChannel;
            }
        }
    }

    public void upsample() {
        for (int c = 0; c < buffer.length; c++)
            buffer[c] = performUpsampling(buffer[c], c);
        width *= header.upsampling;
        height *= header.upsampling;
        header.origin = header.origin.times(header.upsampling);
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
        float[][][] localNoiseBuffer = new float[3][header.height][header.width];
        int numGroups = rowStride * MathHelper.ceilDiv(header.height, header.groupDim);
        TaskList<Void> tasks = new TaskList<>(flowHelper.getThreadPool());
        for (int group = 0; group < numGroups; group++) {
            IntPoint groupXYUp = IntPoint.coordinates(group, rowStride).times(header.upsampling);
            // SPEC: spec doesn't mention how noise groups interact with upsampling
            for (int iy = 0; iy < header.upsampling; iy++) {
                for (int ix = 0; ix < header.upsampling; ix++) {
                    int x0 = (groupXYUp.x + ix) * header.groupDim;
                    int y0 = (groupXYUp.y + iy) * header.groupDim;
                    tasks.submit(() -> new NoiseGroup(header, seed0, localNoiseBuffer, x0, y0));
                }
            }
        }

        if (laplacian == null) {
            laplacian = new float[5][5];
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    laplacian[i][j] = (i == 2 && j == 2 ? -3.84f : 0.16f);
                }
            }
        }

        noiseBuffer = new float[3][header.height][header.width];
        tasks.collect();

        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < header.height; y++) {
                for (int x = 0; x < header.width; x++) {
                    for (int iy = 0; iy < 5; iy++) {
                        for (int ix = 0; ix < 5; ix++) {
                            int cy = MathHelper.mirrorCoordinate(y + iy - 2, header.height);
                            int cx = MathHelper.mirrorCoordinate(x + ix - 2, header.width);
                            noiseBuffer[c][y][x] += localNoiseBuffer[c][cy][cx] * laplacian[iy][ix];
                        }
                    }
                }
            }
        }
    }

    public void synthesizeNoise() {
        if (lfGlobal.noiseParameters == null)
            return;
        float[] lut = lfGlobal.noiseParameters.lut;
        // header.width here to avoid upsampling
        for (IntPoint p : FlowHelper.range2D(header.width, header.height)) {
            int x = p.x, y = p.y;
            // SPEC: spec doesn't mention the *0.5 here, it says *6
            // SPEC: spec doesn't mention clamping to 0 here
            float inScaledR = 3f * Math.max(0f, buffer[1][y][x] + buffer[0][y][x]);
            float inScaledG = 3f * Math.max(0f, buffer[1][y][x] - buffer[0][y][x]);
            int intInR;
            float fracInR;
            // LIBJXL: libjxl bug makes this >= 6D and 5, making lut[7] unused
            // SPEC: spec bug makes this >= 8D and 7, making lut[8] overflow
            if (inScaledR >= 7f) {
                intInR = 6;
                fracInR = 1f;
            } else {
                intInR = (int)inScaledR;
                fracInR = inScaledR - intInR;
            }
            int intInG;
            float fracInG;
            if (inScaledG >= 7f) {
                intInG = 6;
                fracInG = 1f;
            } else {
                intInG = (int)inScaledG;
                fracInG = inScaledG - intInG;
            }
            float sr = (lut[intInR + 1] - lut[intInR]) * fracInR + lut[intInR];
            float sg = (lut[intInG + 1] - lut[intInG]) * fracInG + lut[intInG];
            float nr = 0.22f * sr * (0.0078125f * noiseBuffer[0][y][x] + 0.9921875f * noiseBuffer[2][y][x]);
            float ng = 0.22f * sg * (0.0078125f * noiseBuffer[1][y][x] + 0.9921875f * noiseBuffer[2][y][x]);
            float nrg = nr + ng;
            buffer[0][y][x] += lfGlobal.lfChanCorr.baseCorrelationX * nrg + nr - ng;
            buffer[1][y][x] += nrg;
            buffer[2][y][x] += lfGlobal.lfChanCorr.baseCorrelationB * nrg;
        }
    }

    public LFGlobal getLFGlobal() {
        return lfGlobal;
    }

    public HFGlobal getHFGlobal() {
        return hfGlobal;
    }

    public HFPass getHFPass(int index) {
        return passes[index].hfPass;
    }

    public LFGroup getLFGroupForGroup(int group) {
        return lfGroups[groupXY(group).shiftRight(3).unwrapCoord(lfGroupRowStride)];
    }

    public int getNumLFGroups() {
        return numLFGroups;
    }

    public int getNumGroups() {
        return numGroups;
    }

    public int getGroupRowStride() {
        return groupRowStride;
    }

    public int getLFGroupRowStride() {
        return lfGroupRowStride;
    }

    public float[][][] getBuffer() {
        return buffer;
    }

    public int getColorChannelCount() {
        return globalMetadata.isXYBEncoded() || header.encoding == FrameFlags.VARDCT
            ? 3 : globalMetadata.getColorChannelCount();
    }

    public boolean isDecoded() {
        return this.decoded;
    }

    public IntPoint groupXY(int group) {
        return IntPoint.coordinates(group, groupRowStride);
    }

    public IntPoint getLFGroupXY(int lfGroup) {
        return IntPoint.coordinates(lfGroup, lfGroupRowStride);
    }

    /*
     * @return The position of this group within this LF Group, 1-incrementing
     */
    public IntPoint groupPosInLFGroup(int lfGroupID, int groupID) {
        return groupXY(groupID).minus(getLFGroupXY(lfGroupID).shiftLeft(3));
    }

    public IntPoint groupSize(int group) {
        IntPoint groupxy = groupXY(group);
        IntPoint paddedSize = getPaddedFrameSize();
        return new IntPoint(Math.min(header.groupDim, paddedSize.x - groupxy.x * header.groupDim),
            Math.min(header.groupDim, paddedSize.y - groupxy.y * header.groupDim));
    }

    public IntPoint getLFGroupSize(int lfGroup) {
        IntPoint lfGroupXY = getLFGroupXY(lfGroup);
        IntPoint paddedSize = getPaddedFrameSize();
        return new IntPoint(Math.min(header.lfGroupDim, paddedSize.x - lfGroupXY.x * header.lfGroupDim),
            Math.min(header.lfGroupDim, paddedSize.y - lfGroupXY.y * header.lfGroupDim));
    }

    public IntPoint getPaddedFrameSize() {
        if (header.encoding == FrameFlags.VARDCT)
            return new IntPoint(width, height).ceilDiv(8).times(8);
        else
            return new IntPoint(width, height);
    }

    public IntPoint getModularFrameSize() {
        return new IntPoint(width, height);
    }

    public MATree getGlobalTree() {
        return globalTree;
    }

    public void setGlobalTree(MATree tree) {
        this.globalTree = tree;
    }

    public int getGroupBlockDim() {
        return header.groupDim >> 3;
    }

    public boolean isVisible() {
        return (header.type == FrameFlags.REGULAR_FRAME || header.type == FrameFlags.SKIP_PROGRESSIVE)
            && (header.duration != 0 || header.isLast);
    }

    /* gets the sample for the IMAGE position x and y */
    public float getSample(int c, int x, int y) {
        return x < header.origin.x || y < header.origin.y
            || x - header.origin.x >= width
            || y - header.origin.y >= height
            ? 0 : buffer[c][y - header.origin.y][x - header.origin.x];
    }

    public void printDebugInfo() {
        loggers.log(Loggers.LOG_VERBOSE, "Frame Info:");
        loggers.log(Loggers.LOG_VERBOSE, "    Encoding: %s", header.encoding == FrameFlags.VARDCT ? "VarDCT" : "Modular");
        String type = header.type == FrameFlags.REGULAR_FRAME ? "Regular"
                    : header.type == FrameFlags.LF_FRAME ? "LF Frame"
                    : header.type == FrameFlags.REFERENCE_ONLY ? "Reference Only"
                    : header.type == FrameFlags.SKIP_PROGRESSIVE ? "Skip Progressive"
                    : "????";
        loggers.log(Loggers.LOG_VERBOSE, "    Type: %s", type);
        loggers.log(Loggers.LOG_VERBOSE, "    Size: %dx%d", width, height);
        loggers.log(Loggers.LOG_VERBOSE, "    Origin: (%d, %d)", header.origin.x, header.origin.y);
        loggers.log(Loggers.LOG_VERBOSE, "    YCbCr: %b", header.doYCbCr);
    }

    public Loggers getLoggers() {
        return loggers;
    }
}
