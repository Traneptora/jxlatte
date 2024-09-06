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
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
import com.traneptora.jxlatte.frame.vardct.HFGlobal;
import com.traneptora.jxlatte.frame.vardct.HFPass;
import com.traneptora.jxlatte.frame.vardct.TransformType;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.Loggers;
import com.traneptora.jxlatte.util.Dimension;
import com.traneptora.jxlatte.util.ImageBuffer;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.Point;
import com.traneptora.jxlatte.util.Rectangle;
import com.traneptora.jxlatte.util.functional.ExceptionalFunction;
import com.traneptora.jxlatte.util.functional.FunctionalHelper;

public class Frame {

    public static final int[] cMap = new int[]{1, 0, 2};

    private static Point[] epfCross = new Point[] {
        new Point(0, 0),
        new Point(1, 0), new Point(0, 1),
        new Point(0, -1), new Point(-1, 0),
    };

    private static Point[] epfDoubleCross = new Point[] {
        new Point(0, 0),
        new Point(0, -1), new Point(-1, 0), new Point(0, 1), new Point(1, 0),
        new Point(-1, 1), new Point(1, 1), new Point(-1, -1), new Point(1, -1),
        new Point(0, 2), new Point(2, 0), new Point(0, -2), new Point(-2, 0),
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
    private ImageBuffer[] buffer;
    private float[][][] noiseBuffer;
    private Pass[] passes;
    private boolean decoded = false;
    private HFGlobal hfGlobal;
    private LFGroup[] lfGroups;
    private int groupRowStride;
    private int lfGroupRowStride;
    private MATree globalTree;
    private Rectangle bounds;
    private Loggers loggers;
    private JXLOptions options;

    public Frame(Bitreader reader, ImageHeader globalMetadata, Loggers loggers, JXLOptions options) {
        this.globalReader = reader;
        this.globalMetadata = globalMetadata;
        this.loggers = loggers;
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
        this.buffer = new ImageBuffer[frame.buffer.length];
        for (int c = 0; c < this.buffer.length; c++) {
            this.buffer[c] = new ImageBuffer(frame.buffer[c], copyBuffer);
        }
        this.loggers = frame.loggers;
        this.options = frame.options;
        this.bounds = new Rectangle(frame.bounds);
        this.groupRowStride = frame.groupRowStride;
        this.lfGroupRowStride = frame.lfGroupRowStride;
    }

    public FrameHeader readFrameHeader() throws IOException {
        globalReader.zeroPadToByte();
        this.header = new FrameHeader(globalReader, this.globalMetadata);
        this.bounds = new Rectangle(header.bounds);
        groupRowStride = MathHelper.ceilDiv(bounds.size.width, header.groupDim);
        lfGroupRowStride = MathHelper.ceilDiv(bounds.size.width, header.groupDim << 3);
        numGroups = groupRowStride * MathHelper.ceilDiv(bounds.size.height, header.groupDim);
        numLFGroups = lfGroupRowStride * MathHelper.ceilDiv(bounds.size.height, header.groupDim << 3);
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

    public void readTOC() throws IOException {
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
            @SuppressWarnings({"unchecked", "rawtypes"})
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

    private ImageBuffer performUpsampling(ImageBuffer ib, int c) {
        int color = getColorChannelCount();
        int k;
        if (c < color)
            k = header.upsampling;
        else
            k = header.ecUpsampling[c - color];
        if (k == 1)
            return ib;
        int depth = c < color ? globalMetadata.getBitDepthHeader().bitsPerSample :
            globalMetadata.getExtraChannelInfo(c - color).bitDepth.bitsPerSample;
        ib.castToFloatIfInt(~(~0 << depth));
        float[][] buffer = ib.getFloatBuffer();
        int l = MathHelper.ceilLog1p(k - 1) - 1;
        float[][][][] upWeights = globalMetadata.getUpWeights()[l];
        float[][] newBuffer = new float[buffer.length * k][];
        for (int y = 0; y < buffer.length; y++) {
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
        }
        return ib;
    }

    private void decodeLFGroups(ImageBuffer[] lfBuffer) throws IOException {

        List<ModularChannel> lfReplacementChannels = new ArrayList<>();
        List<Integer> lfReplacementChannelIndicies = new ArrayList<>();
        for (int i = 0; i < lfGlobal.gModular.stream.getEncodedChannelCount(); i++) {
            ModularChannel chan = lfGlobal.gModular.stream.getChannel(i);
            if (!chan.isDecoded()) {
                if (chan.hshift >= 3 && chan.vshift >= 3) {
                    lfReplacementChannelIndicies.add(i);
                    int height = header.lfGroupDim >> chan.vshift;
                    int width = header.lfGroupDim >> chan.hshift;
                    lfReplacementChannels.add(new ModularChannel(height, width, chan.vshift, chan.hshift));
                }
            }
        }

        lfGroups = new LFGroup[numLFGroups];

        for (int lfGroupID = 0; lfGroupID < numLFGroups; lfGroupID++) {
            Bitreader reader = FunctionalHelper.join(getBitreader(1 + lfGroupID));
            Point lfGroupPos = getLFGroupLocation(lfGroupID);
            ModularChannel[] replaced = lfReplacementChannels.stream().map(ModularChannel::new)
                .toArray(ModularChannel[]::new);
            Dimension frameSize = getPaddedFrameSize();
            for (ModularChannel info : replaced) {
                int lfHeight = frameSize.height >> info.vshift;
                int lfWidth = frameSize.width >> info.hshift;
                info.origin.y = lfGroupPos.y * info.size.height;
                info.origin.x = lfGroupPos.x * info.size.width;
                info.size.height = Math.min(info.size.height, lfHeight - info.origin.y);
                info.size.width = Math.min(info.size.width, lfWidth - info.origin.x);
            }
            lfGroups[lfGroupID] = new LFGroup(reader, this, lfGroupID, replaced, lfBuffer);
        }

        /* populate decoded LF Groups */
        for (int lfGroupID = 0; lfGroupID < numLFGroups; lfGroupID++) {
            for (int j = 0; j < lfReplacementChannelIndicies.size(); j++) {
                int index = lfReplacementChannelIndicies.get(j);
                ModularChannel channel = lfGlobal.gModular.stream.getChannel(index);
                ModularChannel newChannelInfo = lfGroups[lfGroupID].modularLFGroup.getChannel(index);
                int[][] newChannel = newChannelInfo.buffer;
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
        PassGroup[][] passGroups = new PassGroup[numPasses][numGroups];

        for (int pass0 = 0; pass0 < numPasses; pass0++) {
            final int pass = pass0;
            for (int group0 = 0; group0 < numGroups; group0++) {
                final int group = group0;
                Bitreader reader = FunctionalHelper.join(getBitreader(2 + numLFGroups + pass * numGroups + group));
                ModularChannel[] replaced = Arrays.asList(passes[pass].replacedChannels).stream()
                    .filter(Objects::nonNull).map(ModularChannel::new).toArray(ModularChannel[]::new);
                for (ModularChannel info : replaced) {
                    int groupHeight = header.groupDim >> info.vshift;
                    int groupWidth = header.groupDim >> info.hshift;
                    int rowStride = MathHelper.ceilDiv(info.size.width, groupWidth);
                    info.origin.y = (group / rowStride) * groupHeight;
                    info.origin.x = (group % rowStride) * groupWidth;
                    info.size.height = Math.min(info.size.height - info.origin.y, groupHeight);
                    info.size.width = Math.min(info.size.width - info.origin.x, groupWidth);
                }
                passGroups[pass][group] = new PassGroup(reader, Frame.this, pass, group, replaced);
            }
        }

        for (int pass = 0; pass < numPasses; pass++) {
            int j = 0;
            for (int i = 0; i < passes[pass].replacedChannels.length; i++) {
                if (passes[pass].replacedChannels[i] == null)
                    continue;
                ModularChannel channel = lfGlobal.gModular.stream.getChannel(i);
                channel.allocate();
                for (int group = 0; group < numGroups; group++) {
                    ModularChannel newChannelInfo = passGroups[pass][group].modularStream.getChannel(j);
                    int[][] buff = newChannelInfo.buffer;
                    for (int y = 0; y < buff.length; y++) {
                        System.arraycopy(buff[y], 0, channel.buffer[y + newChannelInfo.origin.y],
                            newChannelInfo.origin.x, buff[y].length);
                    }
                }
                j++;
            }
        }

        if (header.encoding == FrameFlags.VARDCT) {
            float[][][] buffers = new float[3][][];
            for (int c = 0; c < 3; c++) {
                buffer[c].castToFloatIfInt(~(~0 << globalMetadata.getBitDepthHeader().bitsPerSample));
                buffers[c] = buffer[c].getFloatBuffer();
            }
            for (int pass = 0; pass < numPasses; pass++) {
                for (int group = 0; group < numGroups; group++) {
                    PassGroup passGroup = passGroups[pass][group];
                    PassGroup prev = pass > 0 ? passGroups[pass - 1][group] : null;
                    passGroup.invertVarDCT(buffers, prev);
                }
            }
        }
    }

    public void decodeFrame(ImageBuffer[] lfBuffer) throws IOException {
        if (this.decoded)
            throw new IllegalStateException("Already decoded this frame");
        this.decoded = true;

        lfGlobal = FunctionalHelper.join(getBitreader(0)
            .thenApplyAsync(ExceptionalFunction.of((reader) -> new LFGlobal(reader, this))));
        Dimension paddedSize = getPaddedFrameSize();

        int colors = getColorChannelCount();

        buffer = new ImageBuffer[colors + globalMetadata.getExtraChannelCount()];
        for (int c = 0; c < buffer.length; c++) {
            Dimension channelSize = new Dimension(paddedSize);
            if (c < 3 && c < colors) {
                channelSize.height >>= header.jpegUpsamplingY[c];
                channelSize.width >>= header.jpegUpsamplingX[c];
            }
            boolean isFloat;
            if (c < colors) {
                isFloat = globalMetadata.isXYBEncoded() || header.encoding == FrameFlags.VARDCT ||
                    globalMetadata.getBitDepthHeader().expBits != 0;
            } else {
                isFloat = globalMetadata.getExtraChannelInfo(c - colors).bitDepth.expBits != 0;
            }
            buffer[c] = new ImageBuffer(isFloat ? ImageBuffer.TYPE_FLOAT : ImageBuffer.TYPE_INT,
                channelSize.height, channelSize.width);
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
            boolean isModularColor = header.encoding == FrameFlags.MODULAR && c < getColorChannelCount();
            boolean isModularXYB = globalMetadata.isXYBEncoded() && isModularColor;
            // X, Y, B is encoded as Y, X, (B - Y)
            int cOut = (isModularXYB ? cMap[c] : c) + buffer.length - modularBuffer.length;
            float scaleFactor = isModularXYB ? lfGlobal.lfDequant[cOut] : 1.0f;
            if (isModularXYB && cIn == 2) {
                float[][] outBuffer = buffer[cOut].getFloatBuffer();
                for (int y = 0; y < bounds.size.height; y++) {
                    for (int x = 0; x < bounds.size.width; x++)
                        outBuffer[y][x] = scaleFactor * (modularBuffer[0][y][x] + modularBuffer[2][y][x]);
                }
            } else if (buffer[cOut].isFloat()) {
                float[][] outBuffer = buffer[cOut].getFloatBuffer();
                for (int y = 0; y < bounds.size.height; y++) {
                    for (int x = 0; x < bounds.size.width; x++)
                        outBuffer[y][x] = scaleFactor * modularBuffer[cIn][y][x];
                }
            } else {
                int[][] outBuffer = buffer[cOut].getIntBuffer();
                for (int y = 0; y < bounds.size.height; y++) {
                    System.arraycopy(modularBuffer[cIn][y], 0, outBuffer[y], 0, bounds.size.width);
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
            Point pixelPos = getLFGroupLocation(lfg.lfGroupID);
            pixelPos.y <<= 11;
            pixelPos.x <<= 11;
            float[][][] buff = Stream.of(buffer).map(ImageBuffer::getFloatBuffer).toArray(float[][][]::new);
            for (int i = 0; i < lfg.hfMetadata.blockList.length; i++) {
                Point block = lfg.hfMetadata.blockList[i];
                TransformType tt = lfg.hfMetadata.dctSelect[block.y][block.x];
                float hue = (((float)tt.type * MathHelper.PHI_BAR) % 1.0f) * 2f * (float)Math.PI;
                float rFactor = ((float)Math.cos(hue) + 0.5f) / 1.5f;
                float gFactor = ((float)Math.cos(hue - 2f * (float)Math.PI / 3f) + 1f) / 2f;
                float bFactor = ((float)Math.cos(hue - 4f * (float)Math.PI / 3f) + 1f) / 2f;
                int cornerY = (block.y << 3) + pixelPos.y;
                int cornerX = (block.x << 3) + pixelPos.x;
                for (int y = 0; y < tt.blockHeight; y++) {
                    for (int x = 0; x < tt.blockWidth; x++) {
                        float sampleR = buff[0][y + cornerY][x + cornerX];
                        float sampleG = buff[1][y + cornerY][x + cornerX];
                        float sampleB = buff[2][y + cornerY][x + cornerX];
                        if (x == 0 || y == 0) {
                            buff[1][y + cornerY][x + cornerX] = 0f;
                            buff[0][y + cornerY][x + cornerX] = 0f;
                            buff[2][y + cornerY][x + cornerX] = 0f;
                        } else {
                            float light = 0.25f * (sampleR + sampleB) + 0.5f * sampleG;
                            light = (float)Math.cbrt(light) * 0.5f + 0.25f;
                            buff[0][y + cornerY][x + cornerX] = rFactor * 0.5f + 0.5f * sampleR / light;
                            buff[1][y + cornerY][x + cornerX] = gFactor * 0.5f + 0.5f * sampleG / light;
                            buff[2][y + cornerY][x + cornerX] = bFactor * 0.5f + 0.5f * sampleB / light;
                        }
                    }
                }
            }
        }
    }

    private void performGabConvolution() {
        float[] normGabBase = new float[getColorChannelCount()];
        float[] normGabAdj = new float[normGabBase.length];
        float[] normGabDiag = new float[normGabBase.length];
        for (int c = 0; c < getColorChannelCount(); c++) {
            float gabW1 = header.restorationFilter.gab1Weights[c];
            float gabW2 = header.restorationFilter.gab2Weights[c];
            float mult = 1f / (1f + 4f * (gabW1 + gabW2));
            normGabBase[c] = mult;
            normGabAdj[c] = gabW1 * mult;
            normGabDiag[c] = gabW2 * mult;
        }
        for (int c = 0; c < getColorChannelCount(); c++) {
            buffer[c].castToFloatIfInt(~(~0 << globalMetadata.getBitDepthHeader().bitsPerSample));
            int height = buffer[c].height;
            int width = buffer[c].width;
            float[][] buffC = buffer[c].getFloatBuffer();
            ImageBuffer newBuffer = new ImageBuffer(ImageBuffer.TYPE_FLOAT, height, width);
            float[][] newBufferF = newBuffer.getFloatBuffer();
            for (int y = 0; y < height; y++) {
                int north = (y == 0 ? 0 : y - 1);
                int south = (y + 1 == height) ? height - 1 : y + 1;
                float[] buffR = buffC[y];
                float[] buffN = buffC[north];
                float[] buffS = buffC[south];
                float[] newBuffR = newBufferF[y];
                for (int x = 0; x < width; x++) {
                    int west = (x == 0 ? 0 : x - 1);
                    int east = (x + 1 == width ? width - 1 : x + 1);
                    float adj = buffR[west] + buffR[east] + buffN[x] + buffS[x];
                    float diag = buffN[west] + buffN[east] + buffS[west] + buffS[east];
                    newBuffR[x] = normGabBase[c] * buffR[x] + normGabAdj[c] * adj + normGabDiag[c] * diag;
                }
            }
            buffer[c] = newBuffer;
        }
    }

    private void performEdgePreservingFilter() throws InvalidBitstreamException {
        float stepMultiplier = (float)(1.65D * 4D * (1D - MathHelper.SQRT_H));
        Dimension paddedSize = getPaddedFrameSize();
        int blockHeight = (paddedSize.height + 7) >> 3;
        int blockWidth = (paddedSize.width + 7) >> 3;
        float[][] inverseSigma = new float[blockHeight][blockWidth];
        int dimS = header.logLFGroupDim - 3;
        int colors = getColorChannelCount();

        if (header.encoding == FrameFlags.MODULAR) {
            float inv = 1f / header.restorationFilter.epfSigmaForModular;
            for (int y = 0; y < blockHeight; y++)
                Arrays.fill(inverseSigma[y], inv);
        } else {
            for (int y = 0; y < blockHeight; y++) {
                float[] invSY = inverseSigma[y];
                int lfY = y >> dimS;
                int bY = y - (lfY << dimS);
                int lfR = lfY * lfGroupRowStride;
                for (int x = 0; x < blockWidth; x++) {
                    int lfX = x >> dimS;
                    int bX = x - (lfX << dimS);
                    LFGroup lfg = lfGroups[lfR + lfX];
                    int hf = lfg.hfMetadata.hfMultiplier[bY][bX];
                    int sharpness = lfg.hfMetadata.hfStreamBuffer[3][bY][bX];
                    if (sharpness < 0 || sharpness > 7)
                        throw new InvalidBitstreamException("Invalid EPF Sharpness: " + sharpness);
                    float sigma = hf * header.restorationFilter.epfSharpLut[sharpness];
                    invSY[x] = 1f / sigma;
                }
            }
        }

        ImageBuffer[] outputBuffer = new ImageBuffer[colors];
        for (int c = 0; c < colors; c++) {
            buffer[c].castToFloatIfInt(~(~0 << globalMetadata.getBitDepthHeader().bitsPerSample));
            outputBuffer[c] = new ImageBuffer(ImageBuffer.TYPE_FLOAT, paddedSize.height, paddedSize.width);
        }

        for (int i = 0; i < 3; i++) {
            if (i == 0 && header.restorationFilter.epfIterations < 3)
                continue;
            if (i == 2 && header.restorationFilter.epfIterations < 2)
                break;
            float[][][] inputBuffers = Stream.of(buffer).map(ImageBuffer::getFloatBuffer).toArray(float[][][]::new);
            float[][][] outputBuffers = Stream.of(outputBuffer).map(ImageBuffer::getFloatBuffer).toArray(float[][][]::new);
            float sigmaScale = stepMultiplier * (i == 0 ? header.restorationFilter.epfPass0SigmaScale :
                i == 1 ? 1.0f : header.restorationFilter.epfPass2SigmaScale);
            Point[] crossList = i == 0 ? epfDoubleCross : epfCross;
            for (int y = 0; y < paddedSize.height; y++) {
                float[] invSY = inverseSigma[y >> 3];
                for (int x = 0; x < paddedSize.width; x++) {
                    float sumWeights = 0f;
                    final float[] sumChannels = new float[outputBuffer.length];
                    final float s = invSY[x >> 3];
                    if (s != s || s > 3.333333333f) {
                        for (int c = 0; c < outputBuffer.length; c++)
                            outputBuffers[c][y][x] = inputBuffers[c][y][x];
                        continue;
                    }
                    for (Point cross : crossList) {
                        int nY = y + cross.y;
                        int nX = x + cross.x;
                        int mY = MathHelper.mirrorCoordinate(nY, paddedSize.height);
                        int mX = MathHelper.mirrorCoordinate(nX, paddedSize.width);
                        float dist = i == 2 ? epfDistance2(inputBuffers, colors, y, x, nY, nX, paddedSize)
                            : epfDistance1(inputBuffers, colors, y, x, nY, nX, paddedSize);
                        float weight = epfWeight(sigmaScale, dist, s, y, x);
                        sumWeights += weight;
                        for (int c = 0; c < colors; c++)
                            sumChannels[c] += inputBuffers[c][mY][mX] * weight;
                    }
                    for (int c = 0; c < outputBuffer.length; c++)
                        outputBuffers[c][y][x] = sumChannels[c] / sumWeights;
                }
            }
            for (int c = 0; c < colors; c++) {
                /* swapping lets us reuse the output buffer without reallocating */
                ImageBuffer tmp = buffer[c];
                buffer[c] = outputBuffer[c];
                outputBuffer[c] = tmp;
            }
        }
    }

    private float epfDistance1(float[][][] buffer, int colors, int basePosY, int basePosX,
            int distPosY, int distPosX, Dimension frameSize) {
        float dist = 0;
        for (int c = 0; c < colors; c++) {
            float[][] buffC = buffer[c];
            float scale = header.restorationFilter.epfChannelScale[c];
            for (Point cross : epfCross) {
                int pY = MathHelper.mirrorCoordinate(basePosY + cross.y, frameSize.height);
                int pX = MathHelper.mirrorCoordinate(basePosX + cross.x, frameSize.width);
                int dY = MathHelper.mirrorCoordinate(distPosY + cross.y, frameSize.height);
                int dX = MathHelper.mirrorCoordinate(distPosX + cross.x, frameSize.width);
                dist += Math.abs(buffC[pY][pX] - buffC[dY][dX]) * scale;
            }
        }

        return dist;
    }

    private float epfDistance2(float[][][] buffer, int colors, int basePosY, int basePosX,
            int distPosY, int distPosX, Dimension frameSize) {
        float dist = 0f;
        for (int c = 0; c < colors; c++) {
            float[][] buffC = buffer[c];
            int dY = MathHelper.mirrorCoordinate(distPosY, frameSize.height);
            int dX = MathHelper.mirrorCoordinate(distPosX, frameSize.width);
            dist += Math.abs(buffC[basePosY][basePosX] - buffC[dY][dX]) * header.restorationFilter.epfChannelScale[c];
        }
        return dist;
    }

    private float epfWeight(float stepMultiplier, float distance, float inverseSigma, int refY, int refX) {
        int modY = refY & 0b111;
        int modX = refX & 0b111;
        if (modX == 0 || modX == 7 || modY == 0 || modY == 7)
            distance *= header.restorationFilter.epfBorderSadMul;

        float v = 1f - distance * stepMultiplier * inverseSigma;
        return v < 0f ? 0f : v;
    }

    private void invertSubsampling() {
        for (int c = 0; c < 3; c++) {
            int xShift = header.jpegUpsamplingX[c];
            while (xShift-- > 0) {
                ImageBuffer oldBuffer = buffer[c];
                oldBuffer.castToFloatIfInt(~(~0 << globalMetadata.getBitDepthHeader().bitsPerSample));
                float[][] oldChannel = oldBuffer.getFloatBuffer();
                ImageBuffer newBuffer = new ImageBuffer(ImageBuffer.TYPE_FLOAT, oldBuffer.height, oldBuffer.width * 2);
                float[][] newChannel = newBuffer.getFloatBuffer();
                for (int y = 0; y < oldChannel.length; y++) {
                    float[] oldRow = oldChannel[y];
                    float[] newRow = newChannel[y];
                    for (int x = 0; x < oldRow.length; x++) {
                        float b75 = 0.75f * oldRow[x];
                        newRow[2*x] = b75 + 0.25f * oldRow[x == 0 ? 0 : x - 1];
                        newRow[2*x + 1] = b75 + 0.25f * oldRow[x + 1 == oldRow.length ? oldRow.length - 1 : x + 1];
                    }
                }
                buffer[c] = newBuffer;
            }
            int yShift =  header.jpegUpsamplingY[c];
            while (yShift-- > 0) {
                ImageBuffer oldBuffer = buffer[c];
                oldBuffer.castToFloatIfInt(~(~0 << globalMetadata.getBitDepthHeader().bitsPerSample));
                float[][] oldChannel = oldBuffer.getFloatBuffer();
                ImageBuffer newBuffer = new ImageBuffer(ImageBuffer.TYPE_FLOAT, oldBuffer.height * 2, oldBuffer.width);
                float[][] newChannel = newBuffer.getFloatBuffer();
                for (int y = 0; y < oldChannel.length; y++) {
                    float[] oldRow = oldChannel[y];
                    float[] oldRowPrev = oldChannel[y == 0 ? 0 : y - 1];
                    float[] oldRowNext = oldChannel[y + 1 == oldChannel.length ? oldChannel.length - 1 : y + 1];
                    float[] firstNewRow = newChannel[2*y];
                    float[] secondNewRow = newChannel[2*y + 1];
                    for (int x = 0; x < oldRow.length; x++) {
                        float b75 = 0.75f * oldRow[x];
                        firstNewRow[x] = b75 + 0.25f * oldRowPrev[x];
                        secondNewRow[x] = b75 + 0.25f * oldRowNext[x];
                    }
                }
                buffer[c] = newBuffer;
            }
        }
    }

    public void upsample() {
        for (int c = 0; c < buffer.length; c++) {
            buffer[c] = performUpsampling(buffer[c], c);
        }
        bounds.size.height *= header.upsampling;
        bounds.size.width *= header.upsampling;
        bounds.origin.y *= header.upsampling;
        bounds.origin.x *= header.upsampling;
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
        int colors = getColorChannelCount();
        float[][][] localNoiseBuffer = new float[colors][bounds.size.height][bounds.size.width];
        NoiseGroup[] noiseGroups = new NoiseGroup[numGroups];
        for (int group = 0; group < numGroups; group++) {
            Point groupLocation = getGroupLocation(group);
            noiseGroups[group] = new NoiseGroup(header, seed0, localNoiseBuffer, groupLocation.y, groupLocation.x);
        }

        if (laplacian == null) {
            laplacian = new float[5][5];
            for (int i = 0; i < 5; i++)
                Arrays.fill(laplacian[i], 0.16f);
            laplacian[2][2] = -3.84f;
        }

        noiseBuffer = new float[colors][bounds.size.height][bounds.size.width];

        for (int c = 0; c < colors; c++) {
            for (int y = 0; y < bounds.size.height; y++) {
                for (int x = 0; x < bounds.size.width; x++) {
                    for (int iy = 0; iy < 5; iy++) {
                        for (int ix = 0; ix < 5; ix++) {
                            int cy = MathHelper.mirrorCoordinate(y + iy - 2, bounds.size.height);
                            int cx = MathHelper.mirrorCoordinate(x + ix - 2, bounds.size.width);
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
        int colors = getColorChannelCount();
        float[][][] buffers = new float[3][][];
        for (int c = 0; c < 3; c++) {
            int d = c < colors ? c : 0;
            buffer[d].castToFloatIfInt(~(~0 << globalMetadata.getBitDepthHeader().bitsPerSample));
            buffers[c] = buffer[d].getFloatBuffer();
        }
        // header.width here to avoid upsampling
        for (int y = 0; y < header.bounds.size.height; y++) {
            for (int x = 0; x < header.bounds.size.width; x++) {
                float inScaledR = buffers[1][y][x] + buffers[0][y][x];
                inScaledR = inScaledR < 0f ? 0f : 3f * inScaledR;
                float inScaledG = buffers[1][y][x] - buffers[0][y][x];
                inScaledG = inScaledG < 0f ? 0f : 3f * inScaledG;
                int intInR;
                float fracInR;
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
                float nr = sr * (0.00171875f * noiseBuffer[0][y][x] + 0.21828125f * noiseBuffer[2][y][x]);
                float ng = sg * (0.00171875f * noiseBuffer[1][y][x] + 0.21828125f * noiseBuffer[2][y][x]);
                float nrg = nr + ng;
                buffers[1][y][x] += nrg;
                if (buffers[0] != buffers[1])
                    buffers[0][y][x] += lfGlobal.lfChanCorr.baseCorrelationX * nrg + nr - ng;
                if (buffers[2] != buffers[1])
                    buffers[2][y][x] += lfGlobal.lfChanCorr.baseCorrelationB * nrg;
            }
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

    public LFGroup getLFGroupForGroup(int groupID) {
        Point pos = getGroupLocation(groupID);
        return lfGroups[(pos.y >> 3) * lfGroupRowStride + (pos.x >> 3)];
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

    public ImageBuffer[] getBuffer() {
        return buffer;
    }

    public int getColorChannelCount() {
        return globalMetadata.isXYBEncoded() || header.encoding == FrameFlags.VARDCT
            ? 3 : globalMetadata.getColorChannelCount();
    }

    public boolean isDecoded() {
        return this.decoded;
    }

    public Point getGroupLocation(int groupID) {
        return new Point(groupID / groupRowStride, groupID % groupRowStride);
    }

    /**
     * returns the location of the LF Group, in LF Group units
     */
    public Point getLFGroupLocation(int lfGroupID) {
        return new Point(lfGroupID / lfGroupRowStride, lfGroupID % lfGroupRowStride);
    }

    /*
     * @return The position of this group within this LF Group, 1-incrementing
     */
    public Point groupPosInLFGroup(int lfGroupID, int groupID) {
        Point gr = getGroupLocation(groupID);
        Point lf = getLFGroupLocation(lfGroupID);
        gr.y -= lf.y << 3;
        gr.x -= lf.x << 3;
        return gr;
    }

    public Dimension getGroupSize(int groupID) {
        Point pos = getGroupLocation(groupID);
        Dimension paddedSize = getPaddedFrameSize();
        int height = Math.min(header.groupDim, paddedSize.height - pos.y * header.groupDim);
        int width = Math.min(header.groupDim, paddedSize.width - pos.x * header.groupDim);
        return new Dimension(height, width);
    }

    /**
     * returns the size of the LF Group, in pixels
     */
    public Dimension getLFGroupSize(int lfGroupID) {
        Point pos = getLFGroupLocation(lfGroupID);
        Dimension paddedSize = getPaddedFrameSize();
        int height = Math.min(header.lfGroupDim, paddedSize.height - pos.y * header.lfGroupDim);
        int width = Math.min(header.lfGroupDim, paddedSize.width - pos.x * header.lfGroupDim);
        return new Dimension(height, width);
    }

    public Dimension getPaddedFrameSize() {
        int factorY = 1 << IntStream.of(header.jpegUpsamplingY).max().getAsInt();
        int factorX = 1 << IntStream.of(header.jpegUpsamplingX).max().getAsInt();
        int height, width;
        if (header.encoding == FrameFlags.VARDCT) {
            height = (bounds.size.height + 7) >> 3;
            width = (bounds.size.width + 7) >> 3;
        } else {
            height = bounds.size.height;
            width = bounds.size.width;
        }
        height = MathHelper.ceilDiv(height, factorY);
        width = MathHelper.ceilDiv(width, factorX);
        if (header.encoding == FrameFlags.VARDCT)
            return new Dimension((height * factorY) << 3, (width * factorX) << 3);
        else
            return new Dimension(height * factorY, width * factorX);
    }

    public Dimension getModularFrameSize() {
        return bounds.size;
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

    public void printDebugInfo() {
        loggers.log(Loggers.LOG_VERBOSE, "Frame Info:");
        loggers.log(Loggers.LOG_VERBOSE, "    Encoding: %s",
            header.encoding == FrameFlags.VARDCT ? "VarDCT" : "Modular");
        String type = header.type == FrameFlags.REGULAR_FRAME ? "Regular"
                    : header.type == FrameFlags.LF_FRAME ? "LF Frame"
                    : header.type == FrameFlags.REFERENCE_ONLY ? "Reference Only"
                    : header.type == FrameFlags.SKIP_PROGRESSIVE ? "Skip Progressive"
                    : "????";
        loggers.log(Loggers.LOG_VERBOSE, "    Type: %s", type);
        loggers.log(Loggers.LOG_VERBOSE, "    Size: %dx%d", bounds.size.width, bounds.size.height);
        loggers.log(Loggers.LOG_VERBOSE, "    Origin: (%d, %d)", bounds.origin.x, bounds.origin.y);
        loggers.log(Loggers.LOG_VERBOSE, "    YCbCr: %b", header.doYCbCr);
    }

    public Loggers getLoggers() {
        return loggers;
    }
}
