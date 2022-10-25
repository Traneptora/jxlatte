package com.thebombzen.jxlatte.frame.modular;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.io.Bitreader;

public class ModularStream {
    private int channelCount;
    private int nbMetaChannels = 0;
    public final int streamIndex;

    public final MATree tree;
    public final WPHeader wpParams;
    public final TransformInfo[] transforms;
    public final Frame frame;

    private List<ModularChannel> channels = new LinkedList<>();

    public ModularStream(Bitreader reader, MATree globalTree, Frame frame, int streamIndex, int cCount, int ecStart) throws IOException {
        this.channelCount = cCount;
        this.frame = frame;
        this.streamIndex = streamIndex;
        if (channelCount == 0) {
            tree = null;
            wpParams = null;
            transforms = null;
            return;
        }
        boolean useGlobalTree = reader.readBool();
        wpParams = new WPHeader(reader);
        int nbTransforms = reader.readU32(0, 0, 10, 0, 2, 4, 18, 8);
        transforms = new TransformInfo[nbTransforms];
        for (int i = 0; i < nbTransforms; i++)
            transforms[i] = new TransformInfo(reader);
        int w = frame.getFrameHeader().width;
        int h = frame.getFrameHeader().height;
        for (int i = 0; i < channelCount; i++) {
            int dimShift = i < ecStart ? 0 : frame.globalMetadata.getExtraChannelInfo(i - ecStart).dimShift;
            channels.add(new ModularChannel(this, w, h, dimShift));
        }
        for (int i = 0; i < nbTransforms; i++) {
            if (transforms[i].tr == TransformInfo.PALETTE) {
                if (transforms[i].beginC < nbMetaChannels)
                    nbMetaChannels += 2 - transforms[i].numC;
                else
                    nbMetaChannels++;
                for (int j = transforms[i].beginC + 1; j < transforms[i].beginC + transforms[i].numC; j++)
                    channels.remove(j);
                channels.add(0, new ModularChannel(this, transforms[i].nbColors, transforms[i].numC, -1));
            } else if (transforms[i].tr == TransformInfo.SQUEEZE) {
                int begin = transforms[i].beginC;
                int end = begin + transforms[i].numC - 1;
                for (int j = 0; j < transforms[i].sp.length; j++) {
                    int r = transforms[i].sp[j].inPlace ? end + 1 : channels.size();
                    if (begin < nbMetaChannels) {
                        if (!transforms[i].sp[j].inPlace)
                            throw new InvalidBitstreamException("squeeze meta must be in place");
                        if (end >= nbMetaChannels)
                            throw new InvalidBitstreamException("squeeze meta must end in meta");
                        nbMetaChannels += transforms[i].sp[j].numC;
                    }
                    for (int k = begin; k <= end; k++) {
                        ModularChannel residu;
                        ModularChannel chan = channels.get(k);
                        if (transforms[i].sp[j].horizontal) {
                            w = chan.width;
                            chan.width = (w + 1) / 2;
                            chan.hshift++;
                            residu = new ModularChannel(chan);
                            residu.width = w / 2;
                        } else {
                            h = chan.height;
                            chan.height = (h + 1) / 2;
                            chan.vshift++;
                            residu = new ModularChannel(chan);
                            residu.height = h / 2;
                        }
                        channels.add(r + k - begin, residu);
                    }
                }
            }
        }

        if (!useGlobalTree)
            tree = new MATree(reader);
        else
            tree = globalTree;

        int distanceMultiplier = 0;
        for (int i = nbMetaChannels; i < channels.size(); i++) {
            w = channels.get(i).width;
            if (w > distanceMultiplier)
                distanceMultiplier = w;
        }        
        

    }
}
