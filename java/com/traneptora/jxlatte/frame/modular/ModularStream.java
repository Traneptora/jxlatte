package com.traneptora.jxlatte.frame.modular;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.Loggers;
import com.traneptora.jxlatte.util.functional.ExceptionalIntBiConsumer;

public class ModularStream {

    private static final int[][] kDeltaPalette = {
        {0, 0, 0}, {4, 4, 4}, {11, 0, 0}, {0, 0, -13}, {0, -12, 0}, {-10, -10, -10},
        {-18, -18, -18}, {-27, -27, -27}, {-18, -18, 0}, {0, 0, -32}, {-32, 0, 0}, {-37, -37, -37},
        {0, -32, -32}, {24, 24, 45}, {50, 50, 50}, {-45, -24, -24}, {-24, -45, -45}, {0, -24, -24},
        {-34, -34, 0}, {-24, 0, -24}, {-45, -45, -24}, {64, 64, 64}, {-32, 0, -32}, {0, -32, 0},
        {-32, 0, 32}, {-24, -45, -24}, {45, 24, 45}, {24, -24, -45}, {-45, -24, 24}, {80, 80, 80},
        {64, 0, 0}, {0, 0, -64}, {0, -64, -64}, {-24, -24, 45}, {96, 96, 96}, {64, 64, 0},
        {45, -24, -24}, {34, -34, 0}, {112, 112, 112}, {24, -45, -45}, {45, 45, -24}, {0, -32, 32},
        {24, -24, 45}, {0, 96, 96}, {45, -24, 24}, {24, -45, -24}, {-24, -45, 24}, {0, -64, 0},
        {96, 0, 0}, {128, 128, 128}, {64, 0, 64}, {144, 144, 144}, {96, 96, 0}, {-36, -36, 36},
        {45, -24, -45}, {45, -45, -24}, {0, 0, -96}, {0, 128, 128}, {0, 96, 0}, {45, 24, -45},
        {-128, 0, 0}, {24, -45, 24}, {-45, 24, -45}, {64, 0, -64}, {64, -64, -64}, {96, 0, 96},
        {45, -45, 24}, {24, 45, -45}, {64, 64, -64}, {128, 128, 0}, {0, 0, -128}, {-24, 45, -45} };
    
    private static final int[][] permutationLut = new int[][]{
        {0, 1, 2}, {1, 2, 0}, {2, 0, 1},
        {0, 2, 1}, {1, 0, 2}, {2, 1, 0},
    };

    private int nbMetaChannels = 0;
    private int streamIndex;
    private int distMultiplier;

    private MATree tree;
    private WPParams wpParams;
    private TransformInfo[] transforms;
    private Frame frame;
    private EntropyStream stream = null;
    private boolean transformed = false;

    private List<ModularChannelInfo> channels = new ArrayList<>();

    private Map<Integer, SqueezeParam[]> squeezeMap = new HashMap<>();

    public ModularStream(Bitreader reader, Frame frame,
            int streamIndex, int channelCount, int ecStart) throws IOException {
        this(reader, frame, streamIndex, channelCount, ecStart, null);
    }

    public ModularStream(Bitreader reader, Frame frame,
            int streamIndex, ModularChannelInfo[] channelArray) throws IOException {
        this(reader, frame, streamIndex, channelArray.length, 0, channelArray);
    }

    private ModularStream(Bitreader reader, Frame frame, int streamIndex, int channelCount, int ecStart, ModularChannelInfo[] channelArray) throws IOException {
        this.frame = frame;
        this.streamIndex = streamIndex;
        if (channelCount == 0) {
            tree = null;
            wpParams = null;
            transforms = new TransformInfo[0];
            distMultiplier = 1;
            return;
        }
        boolean useGlobalTree = reader.readBool();
        wpParams = new WPParams(reader);
        int nbTransforms = reader.readU32(0, 0, 1, 0, 2, 4, 18, 8);
        transforms = new TransformInfo[nbTransforms];
        for (int i = 0; i < nbTransforms; i++)
            transforms[i] = new TransformInfo(reader);

        int w = frame.getModularFrameSize().x;
        int h = frame.getModularFrameSize().y;

        if (channelArray == null) {
            for (int i = 0; i < channelCount; i++) {
                int dimShift = i < ecStart ? 0 : frame.globalMetadata.getExtraChannelInfo(i - ecStart).dimShift;
                channels.add(new ModularChannelInfo(w, h, dimShift, dimShift));
            }
        } else {
            channels.addAll(Arrays.asList(channelArray));
        }

        frame.getLoggers().log(Loggers.LOG_TRACE, "Transforms: %s", (Object)transforms);

        for (int i = 0; i < nbTransforms; i++) {
            if (transforms[i].tr == TransformInfo.PALETTE) {
                if (transforms[i].beginC < nbMetaChannels)
                    nbMetaChannels += 2 - transforms[i].numC;
                else
                    nbMetaChannels++;
                int start = transforms[i].beginC + 1;
                for (int j = start; j < transforms[i].beginC + transforms[i].numC; j++)
                    channels.remove(start);
                if (transforms[i].nbDeltas > 0 && transforms[i].dPred == 6)
                    channels.get(transforms[i].beginC).forceWP = true;
                channels.add(0, new ModularChannelInfo(transforms[i].nbColors, transforms[i].numC, -1, -1));
            } else if (transforms[i].tr == TransformInfo.SQUEEZE) {
                List<SqueezeParam> squeezeList = new ArrayList<>();
                if (transforms[i].sp.length == 0) {
                    int first = nbMetaChannels;
                    int count = channels.size() - first;
                    w = channels.get(first).width;
                    h = channels.get(first).height;
                    if (count > 2 && channels.get(first + 1).width == w && channels.get(first + 1).height == h) {
                        squeezeList.add(new SqueezeParam(true, false, first + 1, 2));
                        squeezeList.add(new SqueezeParam(false, false, first + 1, 2));
                    }
                    if (h >= w && h > 8) {
                        squeezeList.add(new SqueezeParam(false, true, first, count));
                        h = (h + 1) / 2;
                    }
                    while (w > 8 || h > 8) {
                        if (w > 8) {
                            squeezeList.add(new SqueezeParam(true, true, first, count));
                            w = (w + 1) / 2;
                        }
                        if (h > 8) {
                            squeezeList.add(new SqueezeParam(false, true, first, count));
                            h = (h + 1) / 2;
                        }
                    }
                } else {
                    squeezeList.addAll(Arrays.asList(transforms[i].sp));
                }
                SqueezeParam[] spa = squeezeList.stream().toArray(SqueezeParam[]::new);
                squeezeMap.put(i, spa);
                for (int j = 0; j < spa.length; j++) {
                    int begin = spa[j].beginC;
                    int end = begin + spa[j].numC - 1;
                    int offset = spa[j].inPlace ? end + 1 : channels.size();
                    if (begin < nbMetaChannels) {
                        if (!spa[j].inPlace)
                            throw new InvalidBitstreamException("squeeze meta must be in place");
                        if (end >= nbMetaChannels)
                            throw new InvalidBitstreamException("squeeze meta must end in meta");
                        nbMetaChannels += spa[j].numC;
                    }
                    for (int k = begin; k <= end; k++) {
                        ModularChannelInfo residu;
                        ModularChannelInfo chan = channels.get(k);
                        int r = offset + k - begin;
                        if (spa[j].horizontal) {
                            w = chan.width;
                            chan.width = (w + 1) / 2;
                            chan.hshift++;
                            residu = new ModularChannelInfo(chan);
                            residu.width = w / 2;
                        } else {
                            h = chan.height;
                            chan.height = (h + 1) / 2;
                            chan.vshift++;
                            residu = new ModularChannelInfo(chan);
                            residu.height = h / 2;
                        }
                        channels.add(r, residu);
                    }
                }
            } else if (transforms[i].tr == TransformInfo.RCT) {
                // RCT doesn't modify the channel list
                continue;
            } else {
                throw new InvalidBitstreamException("Illegal Transform " + i + ": " + transforms[i].tr);
            }
        }
        if (!useGlobalTree) {
            tree = new MATree(frame.getLoggers(), reader);
        } else {
            tree = frame.getGlobalTree();
        }
        stream = new EntropyStream(tree.getStream());

        distMultiplier = channels.stream().mapToInt(c -> c.width).reduce(0, Math::max);
    }

    public void decodeChannels(Bitreader reader) throws IOException {
        decodeChannels(reader, false);
    }

    public void decodeChannels(Bitreader reader, boolean partial) throws IOException {
        for (int i = 0; i < channels.size(); i++) {
            ModularChannelInfo info = channels.get(i);
            channels.set(i, new ModularChannel(info));
        }
        int groupDim = frame.getFrameHeader().groupDim;
        for (int i = 0; i < channels.size(); i++) {
            ModularChannel channel = getChannel(i);
            if (partial && i >= nbMetaChannels && (channel.width > groupDim || channel.height > groupDim))
                break;
            channel.decode(reader, stream, wpParams, tree, this, i, streamIndex, distMultiplier);
        }
        if (stream != null && !stream.validateFinalState())
            throw new InvalidBitstreamException("Illegal final modular state");
        if (!partial)
            applyTransforms();
    }

    public int getEncodedChannelCount() {
        return channels.size();
    }

    public ModularChannel getChannel(int index) {
        ModularChannelInfo channel = channels.get(index);
        if (channel instanceof ModularChannel)
            return (ModularChannel)channel;
        else
            throw new IllegalStateException("Channel has not been decoded or allocated yet");
    }

    public ModularChannelInfo getChannelInfo(int index) {
        return channels.get(index);
    }

    public void applyTransforms() {
        if (transformed)
            return;
        transformed = true;
        for (int i = transforms.length - 1; i >= 0; i--) {
            if (transforms[i].tr == TransformInfo.SQUEEZE) {
                SqueezeParam[] spa = squeezeMap.get(i);
                for (int j = spa.length - 1; j >= 0; j--) {
                    SqueezeParam sp = spa[j];
                    int begin = sp.beginC;
                    int end = begin + sp.numC - 1;
                    int offset = sp.inPlace ? end + 1 : channels.size() + begin - end - 1;
                    for (int c = begin; c <= end; c++) {
                        int r = offset + c - begin;
                        ModularChannel chan = getChannel(c);
                        ModularChannel residu = getChannel(r);
                        ModularChannel output;
                        if (sp.horizontal) {
                            ModularChannelInfo outputInfo = new ModularChannelInfo(chan.width + residu.width,
                                chan.height, chan.hshift - 1, chan.vshift);
                            output = ModularChannel.inverseHorizontalSqueeze(frame.getFlowHelper(), outputInfo, chan, residu);
                        } else {
                            ModularChannelInfo outputInfo = new ModularChannelInfo(chan.width,
                                chan.height + residu.height, chan.hshift, chan.vshift - 1);
                            output = ModularChannel.inverseVerticalSqueeze(frame.getFlowHelper(), outputInfo, chan, residu);
                        }
                        channels.set(c, output);
                    }
                    for (int c = 0; c < end - begin + 1; c++)
                        channels.remove(offset);
                }
            } else if (transforms[i].tr == TransformInfo.RCT) {
                int permutation = transforms[i].rctType / 7;
                int type = transforms[i].rctType % 7;
                ModularChannel[] v = new ModularChannel[3];
                int start = transforms[i].beginC;
                for (int j = 0; j < 3; j++)
                    v[j] = getChannel(start + j);
                ExceptionalIntBiConsumer rct;
                switch(type) {
                    case 0:
                        rct = (x, y) -> {};
                        break;
                    case 1:
                        rct = (x, y) -> {
                            v[2].buffer[y][x] = v[0].buffer[y][x] + v[2].buffer[y][x];
                        };
                        break;
                    case 2:
                        rct = (x, y) -> {
                            v[1].buffer[y][x] = v[0].buffer[y][x] + v[1].buffer[y][x];
                        };
                        break;
                    case 3:
                        rct = (x, y) -> {
                            final int a = v[0].buffer[y][x];
                            v[2].buffer[y][x] = a + v[2].buffer[y][x];
                            v[1].buffer[y][x] = a + v[1].buffer[y][x];
                        };
                        break;
                    case 4:
                        rct = (x, y) -> {
                            v[1].buffer[y][x] = v[1].buffer[y][x] + (v[0].buffer[y][x] + v[2].buffer[y][x]) >> 1;
                        };
                        break;
                    case 5:
                        rct = (x, y) -> {
                            final int a = v[0].buffer[y][x];
                            final int c = v[2].buffer[y][x];
                            v[1].buffer[y][x] = v[1].buffer[y][x] + a + (c >> 1);
                            v[2].buffer[y][x] = a + c;
                        };
                        break;
                    case 6:
                        rct = (x, y) -> {
                            final int b = v[1].buffer[y][x];
                            final int c = v[2].buffer[y][x];
                            final int tmp = v[0].buffer[y][x] - (c >> 1);
                            final int f = tmp - (b >> 1);
                            v[0].buffer[y][x] = f + b;
                            v[1].buffer[y][x] = c + tmp;
                            v[2].buffer[y][x] = f;
                        };
                        break;
                    default:
                        throw new IllegalStateException("Challenge complete how did we get here");
                }
                for (int y = 0; y < v[0].height; y++) {
                    for (int x = 0; x < v[0].width; x++)
                        rct.consume(x, y);
                }
                for (int j = 0; j < 3; j++)
                    channels.set(start + permutationLut[permutation][j], v[j]);
            } else if (transforms[i].tr == TransformInfo.PALETTE) {
                int first = transforms[i].beginC + 1;
                int endC = transforms[i].beginC + transforms[i].numC - 1;
                int last = endC + 1;
                int bitDepth = frame.globalMetadata.getBitDepthHeader().bitsPerSample;
                ModularChannel firstChannel = getChannel(first);
                ModularChannel c0 = getChannel(0);
                for (int j = first + 1; j <= last; j++) {
                    channels.add(j, new ModularChannel(firstChannel));
                }
                for (int c = 0; c < transforms[i].numC; c++) {
                    ModularChannel chan = getChannel(first + c);
                    for (int y = 0; y < firstChannel.height; y++) {
                        for (int x = 0; x < firstChannel.width; x++) {
                            int index = chan.buffer[y][x];
                            boolean isDelta = index < transforms[i].nbDeltas;
                            int value;
                            if (index >= 0 && index < transforms[i].nbColors) {
                                value = c0.buffer[c][index];
                            } else if (index >= transforms[i].nbColors) {
                                index -= transforms[i].nbColors;
                                if (index < 64) {
                                    value = ((index >> (2 * c)) % 4) * ((1 << bitDepth) - 1) / 4
                                        + (1 << Math.max(0, bitDepth - 3));
                                } else {
                                    index -= 64;
                                    for (int k = 0; k < c; k++)
                                        index /= 5;
                                    value = (index % 5) * ((1 << bitDepth) - 1) / 4;
                                }
                            } else if (c < 3) {
                                index = (-index - 1) % 143;
                                value = kDeltaPalette[(index + 1) >> 1][c];
                                if ((index & 1) == 0)
                                    value = -value;
                                if (bitDepth > 8)
                                    value <<= Math.min(bitDepth, 24) - 8;
                            } else {
                                value = 0;
                            }
                            chan.buffer[y][x] = value;
                            if (isDelta)
                                chan.buffer[y][x] += chan.prediction(x, y, transforms[i].dPred);
                        }
                    }
                }
                channels.remove(0);
                nbMetaChannels--;
            }
        }
    }

    public EntropyStream getEntropyStream() {
        return stream;
    }

    public int[][][] getDecodedBuffer() {
        int[][][] bands = new int[channels.size()][][];
        for (int i = 0; i < bands.length; i++)
            bands[i] = getChannel(i).buffer;
        return bands;
    }
}
