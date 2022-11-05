package com.thebombzen.jxlatte;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.thebombzen.jxlatte.bundle.ExtraChannelType;
import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.frame.BlendingInfo;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.FrameHeader;
import com.thebombzen.jxlatte.frame.lfglobal.Patch;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.MathHelper;

public class JXLCodestreamDecoder {
    private Bitreader bitreader;
    private ImageHeader imageHeader;

    public JXLCodestreamDecoder(Bitreader in) {
        this.bitreader = in;
    }

    private static int getICCContext(byte[] buffer, int index) {
        if (index <= 128)
            return 0;
        int b1 = (int)buffer[index - 1] & 0xFF;
        int b2 = (int)buffer[index - 2] & 0xFF;
        int p1, p2;
        if (b1 >= 'a' && b1 <= 'z' || b1 >= 'A' && b1 <= 'Z')
            p1 = 0;
        else if (b1 >= '0' && b1 <= '9' || b1 == '.' || b1 == ',')
            p1 = 1;
        else if (b1 <= 1)
            p1 = 2 + b1;
        else if (b1 > 1 && b1 < 16)
            p1 = 4;
        else if (b1 > 240 && b1 < 255)
            p1 = 5;
        else if (b1 == 255)
            p1 = 6;
        else
            p1 = 7;
        
        if (b2 >= 'a' && b2 <= 'z' || b2 >= 'A' && b2 <= 'Z')
            p2 = 0;
        else if (b2 >= '0' && b2 <= '9' || b2 == '.' || b2 == ',')
            p2 = 1;
        else if (b2 < 16)
            p2 = 2;
        else if (b2 > 240)
            p2 = 3;
        else
            p2 = 4;

        return 1 + p1 + 8 * p2;
    }

    private void computePatches(List<? extends Frame> frames) throws InvalidBitstreamException {
        for (int f = 0; f < frames.size(); f++) {
            Frame frame = frames.get(f);
            FrameHeader header = frame.getFrameHeader();
            double[][][] frameBuffer = frame.getBuffer();
            int colorChannels = imageHeader.getColorChannelCount();
            int extraChannels = imageHeader.getExtraChannelCount();
            Patch[] patches = frame.getLFGlobal().patches;
            for (int i = 0; i < patches.length; i++) {
                Patch patch = patches[i];
                if (patch.ref > f)
                    throw new InvalidBitstreamException("Patch out of range");
                Frame ref = frames.get(patch.ref);
                double[][][] refBuffer = ref.getBuffer();
                for (int j = 0; j < patch.positions.length; j++) {
                    int x0 = patch.positions[j].x;
                    int y0 = patch.positions[j].y;
                    if (x0 < 0 || y0 < 0)
                        throw new InvalidBitstreamException("Patch size out of bounds");
                    if (patch.height + patch.y0 > ref.getFrameHeader().height
                        || patch.width + patch.x0 > ref.getFrameHeader().width)
                        throw new InvalidBitstreamException("Patch too large");
                    if (patch.height + y0 > frame.getFrameHeader().height
                        || patch.width + x0 > frame.getFrameHeader().width)
                        throw new InvalidBitstreamException("Patch size out of bounds");
                    for (int d = 0; d < colorChannels + extraChannels; d++) {
                        int c = d < colorChannels ? 0 : d - colorChannels + 1;
                        BlendingInfo info = patch.blendingInfos[j][c];
                        if (info.mode == 0)
                            continue;
                        boolean premult = imageHeader.hasAlpha()
                            ? imageHeader.getExtraChannelInfo(info.alphaChannel).alphaAssociated
                            : true;
                        boolean isAlpha = c > 0 &&
                            imageHeader.getExtraChannelInfo(c - 1).type == ExtraChannelType.ALPHA;
                        if (info.mode > 3 && header.upsampling > 1 && c > 0 &&
                                header.ecUpsampling[c - 1] << imageHeader.getExtraChannelInfo(c - 1).dimShift
                                != header.upsampling) {
                            throw new InvalidBitstreamException("Alpha channel upsampling mismatch during patches");
                        }
                        for (int y = 0; y < patch.height; y++) {
                            for (int x = 0; x < patch.width; x++) {
                                int oldX = x + x0;
                                int oldY = y + y0;
                                int newX = x + patch.x0;
                                int newY = y + patch.y0;
                                double oldSample = frameBuffer[d][oldY][oldX];
                                double newSample = refBuffer[d][newY][newX];
                                double alpha = 0D, newAlpha = 0D, oldAlpha = 0D;
                                if (c > 3) {
                                    newAlpha = imageHeader.hasAlpha()
                                    ? refBuffer[colorChannels + info.alphaChannel][newY][newX]
                                        : 1.0;
                                    oldAlpha = imageHeader.hasAlpha()
                                        ? frameBuffer[colorChannels + info.alphaChannel][oldY][oldX]
                                        : 1.0;
                                    if (c > 5 || isAlpha || !premult) {
                                        alpha = oldAlpha + newAlpha * (1 - oldAlpha);
                                        if (info.clamp)
                                            alpha = MathHelper.clamp(alpha, 0.0D, 1.0D);
                                    }
                                }
                                double sample;
                                switch (info.mode) {
                                    case 0:
                                        sample = oldSample;
                                        break;
                                    case 1:
                                        sample = newSample;
                                        break;
                                    case 2:
                                        sample = oldSample + newSample;
                                        break;
                                    case 3:
                                        sample = oldSample * newSample;
                                        break;
                                    case 4:
                                        sample = isAlpha ? alpha : premult ? newSample + oldSample * (1 - newAlpha)
                                            : (newSample * newAlpha + oldSample * oldAlpha * (1 - newAlpha)) / alpha;
                                        break;
                                    case 5:
                                        sample = isAlpha ? alpha : premult ? oldSample + newSample * (1 - newAlpha)
                                            : (oldSample * newAlpha + newSample * oldAlpha * (1 - newAlpha)) / alpha;
                                        break;
                                    case 6:
                                        sample = isAlpha ? alpha : oldSample + alpha * newSample;
                                        break;
                                    case 7:
                                        sample = isAlpha ? alpha : newSample + alpha * oldSample;
                                        break;
                                    default:
                                        throw new IllegalStateException("Challenge complete how did we get here");
                                }
                                frameBuffer[d][oldY][oldX] = sample;
                            }
                        }
                    }
                }
            }
        }
    }

    public JXLImage decode(int level) throws IOException {
        this.imageHeader = ImageHeader.parse(bitreader, level);
        if (imageHeader.getColorEncoding().useIccProfile) {
            int encodedSize = Math.toIntExact(bitreader.readU64());
            byte[] encodedIcc = new byte[encodedSize];
            EntropyStream iccDistribution = new EntropyStream(bitreader, 41);
            for (int i = 0; i < encodedSize; i++)
                encodedIcc[i] = (byte)iccDistribution.readSymbol(bitreader, getICCContext(encodedIcc, i));
        }
        bitreader.zeroPadToByte();

        Frame frame;
        if (imageHeader.getPreviewHeader() != null) {
            frame = new Frame(bitreader, imageHeader);
            frame.readHeader();
            frame.skipFrameData();
        }

        int height = imageHeader.getSize().height;
        int width = imageHeader.getSize().width;
        double[][][] buffer = new double[imageHeader.getTotalChannelCount()][height][width];

        List<Frame> frames = new ArrayList<>();

        do {
            frame = new Frame(bitreader, imageHeader);
            frame.readHeader();
            frame.decodeFrame();
            frames.add(frame);
        } while (!frame.getFrameHeader().isLast);    

        computePatches(frames);

        for (int i = 0; i < frames.size(); i++) {
            Frame newFrame = frames.get(i);
            if (newFrame.getFrameHeader().type != FrameFlags.REGULAR_FRAME)
                continue;
            int frameWidth = newFrame.getFrameHeader().width;
            int frameHeight = newFrame.getFrameHeader().height;
            int x0 = newFrame.getFrameHeader().x0;
            int y0 = newFrame.getFrameHeader().y0;
            for (int c = 0; c < buffer.length; c++) {
                for (int y = 0; y < frameHeight; y++) {
                    System.arraycopy(newFrame.getBuffer()[c][y], 0, buffer[c][y + y0], x0, frameWidth);
                }
            }
        }

        return new JXLImage(buffer, imageHeader);
    }
}
