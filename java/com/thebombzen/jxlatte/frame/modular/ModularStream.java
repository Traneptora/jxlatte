package com.thebombzen.jxlatte.frame.modular;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.IntBiConsumer;

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

    private int nbMetaChannels = 0;
    private int streamIndex;
    private int distMultiplier;

    private MATree tree;
    private WPHeader wpParams;
    private TransformInfo[] transforms;
    private Frame frame;
    private EntropyStream stream = null;
    private boolean transformed = false;

    private List<ModularChannelInfo> channelInfos = new LinkedList<>();
    private List<ModularChannel> channels = new ArrayList<>();
    private Map<Integer, SqueezeParam[]> spar = new HashMap<>();

    public ModularStream(Bitreader reader, MATree globalTree, Frame frame,
            int streamIndex, int channelCount, int ecStart) throws IOException {
        this(reader, globalTree, frame, streamIndex, channelCount, ecStart, null);
    }

    public ModularStream(Bitreader reader, MATree globalTree, Frame frame,
            int streamIndex, ModularChannelInfo[] channelArray) throws IOException {
        this(reader, globalTree, frame, streamIndex, channelArray.length, 0, channelArray);
    }

    private ModularStream(Bitreader reader, MATree globalTree, Frame frame, int streamIndex, int channelCount, int ecStart, ModularChannelInfo[] channelArray) throws IOException {
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
        wpParams = new WPHeader(reader);
        int nbTransforms = reader.readU32(0, 0, 1, 0, 2, 4, 18, 8);
        transforms = new TransformInfo[nbTransforms];
        for (int i = 0; i < nbTransforms; i++)
            transforms[i] = new TransformInfo(reader);

        int w = frame.getFrameHeader().width;
        int h = frame.getFrameHeader().height;

        if (channelArray == null) {
            for (int i = 0; i < channelCount; i++) {
                int dimShift = i < ecStart ? 0 : frame.globalMetadata.getExtraChannelInfo(i - ecStart).dimShift;
                channelInfos.add(new ModularChannelInfo(w, h, dimShift, dimShift));
            }
        } else {
            channelInfos.addAll(Arrays.asList(channelArray));
        }

        for (int i = 0; i < nbTransforms; i++) {
            if (transforms[i].tr == TransformInfo.PALETTE) {
                if (transforms[i].beginC < nbMetaChannels)
                    nbMetaChannels += 2 - transforms[i].numC;
                else
                    nbMetaChannels++;
                int start = transforms[i].beginC + 1;
                for (int j = start; j < transforms[i].beginC + transforms[i].numC; j++)
                    channelInfos.remove(start);
                if (transforms[i].nbDeltas > 0 && transforms[i].dPred == 6)
                    channelInfos.get(transforms[i].beginC).forceWP = true;
                channelInfos.add(0, new ModularChannelInfo(transforms[i].nbColors, transforms[i].numC, -1, -1));
            } else if (transforms[i].tr == TransformInfo.SQUEEZE) {
                List<SqueezeParam> spar = new ArrayList<>();
                if (transforms[i].sp.length == 0) {
                    int first = nbMetaChannels;
                    int count = channelInfos.size() - first;
                    w = channelInfos.get(first).width;
                    h = channelInfos.get(first).height;
                    if (count > 2 && channelInfos.get(first + 1).width == w && channelInfos.get(first + 1).height == h) {
                       spar.add(new SqueezeParam(true, false, first + 1, 2));
                       spar.add(new SqueezeParam(false, false, first + 1, 2));
                    }
                    if (h >= w && h > 8) {
                        spar.add(new SqueezeParam(false, true, first, count));
                        h = (h + 1) / 2;
                    }
                    while (w > 8 || h > 8) {
                        if (w > 8) {
                            spar.add(new SqueezeParam(true, true, first, count));
                            w = (w + 1) / 2;
                        }
                        if (h > 8) {
                            spar.add(new SqueezeParam(false, true, first, count));
                            h = (h + 1) / 2;
                        }
                    }
                } else {
                    spar.addAll(Arrays.asList(transforms[i].sp));
                }
                SqueezeParam[] spa = spar.stream().toArray(SqueezeParam[]::new);
                this.spar.put(i, spa);
                for (int j = 0; j < spa.length; j++) {
                    int begin = spa[j].beginC;
                    int end = begin + spa[j].numC - 1;
                    int offset = spa[j].inPlace ? end + 1 : channelInfos.size();
                    if (begin < nbMetaChannels) {
                        if (!spa[j].inPlace)
                            throw new InvalidBitstreamException("squeeze meta must be in place");
                        if (end >= nbMetaChannels)
                            throw new InvalidBitstreamException("squeeze meta must end in meta");
                        nbMetaChannels += spa[j].numC;
                    }
                    for (int k = begin; k <= end; k++) {
                        ModularChannelInfo residu;
                        ModularChannelInfo chan = channelInfos.get(k);
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
                        channelInfos.add(r, residu);
                    }
                }
                // RCT doesn't modify the channel list
            }
        }
        if (!useGlobalTree) {
            tree = new MATree(reader);
        } else {
            this.tree = globalTree;
        }
        this.stream = new EntropyStream(tree.getStream());

        int d = 0;               
        for (int i = nbMetaChannels; i < channelInfos.size(); i++) {
            d = Math.max(channelInfos.get(i).width, d);
        }
        distMultiplier = d;
    }

    public void decodeChannels(Bitreader reader, boolean partial) throws IOException {
        int groupDim = frame.getFrameHeader().groupDim;
        for (int i = 0; i < channelInfos.size(); i++) {
            ModularChannelInfo info = channelInfos.get(i);
            if (partial && i >= nbMetaChannels && (info.width > groupDim || info.height > groupDim))
                break;
            ModularChannel channel = new ModularChannel(info);
            channel.decode(reader, stream, wpParams, tree, i, streamIndex, distMultiplier);
            channels.add(channel);
        }
        if (stream != null && !stream.validateFinalState())
            throw new InvalidBitstreamException("Illegal final modular state");
        for (int i = channels.size(); i < channelInfos.size(); i++) {
            channels.add(new ModularChannel(channelInfos.get(i)));
        }
    }

    public int getEncodedChannelCount() {
        return channelInfos.size();
    }

    public ModularChannel getChannel(int index) {
        return channels.get(index);
    }

    public ModularChannelInfo getChannelInfo(int index) {
        return channelInfos.get(index);
    }

    public void applyTransforms() {
        if (transformed)
            return;
        transformed = true;
        for (int i = transforms.length - 1; i >= 0; i--) {

            if (transforms[i].tr == TransformInfo.SQUEEZE) {
                SqueezeParam[] spa = spar.get(i);
                for (int j = spa.length - 1; j >= 0; j--) {
                    SqueezeParam sp = spa[j];
                    int begin = sp.beginC;
                    int end = begin + sp.numC - 1;
                    int offset = sp.inPlace ? end + 1 : channels.size() + begin - end - 1;
                    for (int c = begin; c <= end; c++) {
                        int r = offset + c - begin;
                        ModularChannel chan = channels.get(c);
                        ModularChannel residu = channels.get(r);
                        ModularChannel output;
                        if (sp.horizontal) {
                            ModularChannelInfo outputInfo = new ModularChannelInfo(chan.info.width + residu.info.width,
                                chan.info.height, chan.info.hshift - 1, chan.info.vshift);
                            output = ModularChannel.inverseHorizontalSqueeze(outputInfo, chan, residu);
                        } else {
                            ModularChannelInfo outputInfo = new ModularChannelInfo(chan.info.width,
                                chan.info.height + residu.info.height, chan.info.hshift, chan.info.vshift - 1);
                            output = ModularChannel.inverseVerticalSqueeze(outputInfo, chan, residu);
                        }
                        channels.set(c, output);
                    }
                    for (int c = 0; c < end - begin + 1; c++) {
                        channels.remove(offset);
                    }
                }
            }

            if (transforms[i].tr == TransformInfo.RCT) {
                int permutation = transforms[i].rctType / 7;
                int type = transforms[i].rctType % 7;
                ModularChannel a = channels.get(transforms[i].beginC);
                ModularChannel b = channels.get(transforms[i].beginC + 1);
                ModularChannel c = channels.get(transforms[i].beginC + 2);
                int w = channels.get(transforms[i].beginC).info.width;
                int h = channels.get(transforms[i].beginC).info.height;
                IntBiConsumer rct;
                switch(type) {
                    case 0:
                        rct = (x, y) -> {};
                        break;
                    case 1:
                        rct = (x, y) -> {
                            c.set(x, y, a.get(x, y) + c.get(x, y));
                        };
                        break;
                    case 2:
                        rct = (x, y) -> {
                            c.set(x, y, a.get(x, y) + b.get(x, y));
                        };
                        break;
                    case 3:
                        rct = (x, y) -> {
                            int d = a.get(x, y);
                            c.set(x, y, d + c.get(x, y));
                            b.set(x, y, d + b.get(x, y));
                        };
                        break;
                    case 4:
                        rct = (x, y) -> {
                            b.set(x, y, b.get(x, y) + ((a.get(x, y) + c.get(x, y)) >> 1));
                        };
                        break;
                    case 5:
                        rct = (x, y) -> {
                            b.set(x, y, b.get(x, y) + a.get(x, y) + (c.get(x, y) >> 1));
                            c.set(x, y, c.get(x, y) + a.get(x, y));
                        };
                        break;
                    case 6:
                        rct = (x, y) -> {
                            int tmp = a.get(x, y) - (c.get(x, y) >> 1);
                            int e = c.get(x, y) + tmp;
                            int f = tmp - (b.get(x, y) >> 1);
                            a.set(x, y, f + b.get(x, y));
                            b.set(x, y, e);
                            c.set(x, y, f);
                        };
                        break;
                    default:
                        throw new IllegalStateException("Challenge complete how did we get here");
                }
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        rct.consume(x, y);
                    }
                }
                channels.set(transforms[i].beginC + permutation % 3, a);
                channels.set(transforms[i].beginC + (permutation + 1 + (permutation / 3)) % 3, b);
                channels.set(transforms[i].beginC + (permutation + 2 - (permutation / 3)) % 3, c);
            }

            if (transforms[i].tr == TransformInfo.PALETTE) {
                int first = transforms[i].beginC + 1;
                int endC = transforms[i].beginC + transforms[i].numC - 1;
                int last = endC + 1;
                int bitDepth = frame.globalMetadata.getBitDepthHeader().bitsPerSample;
                ModularChannel firstChannel = channels.get(first);
                for (int j = first + 1; j <= last; j++) {
                    channels.add(j, new ModularChannel(firstChannel));
                }
                for (int c = 0; c < transforms[i].numC; c++) {
                    ModularChannel chan = channels.get(first + c);
                    for (int y = 0; y < firstChannel.info.height; y++) {
                        for (int x = 0; x < firstChannel.info.width; x++) {
                            int index = chan.get(x, y);
                            boolean isDelta = index < transforms[i].nbDeltas;
                            int value;
                            if (index >= 0 && index < transforms[i].nbColors) {
                                value = channels.get(0).get(index, c);
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
                            chan.set(x, y, value);
                            if (isDelta)
                                chan.set(x, y, chan.get(x, y) + chan.prediction(x, y, transforms[i].dPred));
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
            bands[i] = channels.get(i).buffer;
        return bands;
    }
}
