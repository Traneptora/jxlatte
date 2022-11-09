package com.thebombzen.jxlatte;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.thebombzen.jxlatte.bundle.ExtraChannelType;
import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.bundle.color.CIEPrimaries;
import com.thebombzen.jxlatte.bundle.color.CIEXY;
import com.thebombzen.jxlatte.bundle.color.OpsinInverseMatrix;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.frame.BlendingInfo;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.FrameHeader;
import com.thebombzen.jxlatte.frame.features.Patch;
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

    private void computePatches(Frame[] references, Frame frame) throws InvalidBitstreamException {
        FrameHeader header = frame.getFrameHeader();
        double[][][] frameBuffer = frame.getBuffer();
        int colorChannels = imageHeader.getColorChannelCount();
        int extraChannels = imageHeader.getExtraChannelCount();
        Patch[] patches = frame.getLFGlobal().patches;
        for (int i = 0; i < patches.length; i++) {
            Patch patch = patches[i];
            if (patch.ref > 3)
                throw new InvalidBitstreamException("Patch out of range");
            Frame ref = references[patch.ref];
            if (ref == null)
                // this reference is unspecified but not illegal
                continue;
            double[][][] refBuffer = ref.getBuffer();
            for (int j = 0; j < patch.positions.length; j++) {
                int x0 = patch.positions[j].x;
                int y0 = patch.positions[j].y;
                if (x0 < 0 || y0 < 0)
                    throw new InvalidBitstreamException("Patch size out of bounds");
                if (patch.height + patch.origin.y > ref.getFrameHeader().height
                    || patch.width + patch.origin.x > ref.getFrameHeader().width)
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
                            int newX = x + patch.origin.x;
                            int newY = y + patch.origin.y;
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

    public void performColorTransforms(OpsinInverseMatrix matrix, Frame frame) {
        if (matrix != null) {
            matrix.invertXYB(frame.getBuffer(), imageHeader.getToneMapping().intensityTarget);
        }
    }

    public void blendFrame(double[][][] canvas, Frame[] reference, Frame frame) throws InvalidBitstreamException {
        int width = imageHeader.getSize().width;
        int height = imageHeader.getSize().height;
        FrameHeader header = frame.getFrameHeader();
        int frameXStart = Math.max(0, header.origin.x);
        int frameYStart = Math.max(0, header.origin.y);
        int frameXEnd = Math.min(width, header.width + header.origin.x);
        int frameYEnd = Math.min(height, header.height + header.origin.y);
        int colorChannels = imageHeader.getColorChannelCount();
        for (int c = 0; c < canvas.length; c++) {
            double[][] newBuffer = frame.getBuffer()[c];
            BlendingInfo info;
            if (c < colorChannels) {
                info = frame.getFrameHeader().blendingInfo;
            } else {
                info = frame.getFrameHeader().ecBlendingInfo[c - colorChannels];
            }
            boolean isAlpha = c >= colorChannels && imageHeader.getExtraChannelInfo(c - colorChannels).type == ExtraChannelType.ALPHA;
            boolean premult = imageHeader.hasAlpha()
                        ? imageHeader.getExtraChannelInfo(info.alphaChannel).alphaAssociated
                        : true;
            Frame ref = reference[info.source];
            switch (info.mode) {
                case FrameFlags.BLEND_ADD:
                    if (ref != null) {
                        for (int y = frameYStart; y < frameYEnd; y++) {
                            for (int x = frameXStart; x < frameXEnd; x++) {
                                canvas[c][y][x] = frame.getSample(c, x, y) + ref.getSample(c, x, y);
                            }
                        }
                        break;
                    }
                // fall through here is intentional
                case FrameFlags.BLEND_REPLACE:
                    for (int y = frameYStart; y < frameYEnd; y++) {
                        System.arraycopy(newBuffer[y - frameYStart], 0, canvas[c][y], frameXStart, frameXEnd - frameXStart);
                    }
                    break;
                case FrameFlags.BLEND_MULT:
                    for (int y = frameYStart; y < frameYEnd; y++) {
                        if (ref != null) {
                            for (int x = frameXStart; x < frameXEnd; x++) {
                                canvas[c][y][x] = frame.getSample(c, x, y) * ref.getSample(c, x, y);
                            }
                        } else {
                            Arrays.fill(canvas[c][y], frameXStart, frameXEnd, 0D);
                        }
                    }
                    break;
                case FrameFlags.BLEND_BLEND:
                    for (int y = frameYStart; y < frameYEnd; y++) {
                        for (int x = frameXStart; x < frameXEnd; x++) {
                            double oldAlpha = !imageHeader.hasAlpha() ? 1.0D : ref != null ?
                                ref.getSample(colorChannels + info.alphaChannel, x, y) : 0.0D;
                            double newAlpha = !imageHeader.hasAlpha() ? 1.0D
                                : frame.getSample(colorChannels + info.alphaChannel, x, y);
                            double alpha = 1.0D;
                            double oldSample = ref != null ? ref.getSample(c, x, y) : 0.0D;
                            double newSample = frame.getSample(c, x, y);
                            if (isAlpha || !premult) {
                                alpha = oldAlpha + newAlpha * (1 - oldAlpha);
                                if (info.clamp)
                                    alpha = MathHelper.clamp(alpha, 0.0D, 1.0D);
                            }
                            canvas[c][y][x] = isAlpha ? alpha : premult ? newSample + oldSample * (1 - newAlpha)
                            : (newSample * newAlpha + oldSample * oldAlpha * (1 - newAlpha)) / alpha;
                        }
                    }
                    break;
                case FrameFlags.BLEND_MULADD:
                    for (int y = frameYStart; y < frameYEnd; y++) {
                        for (int x = frameXStart; x < frameXEnd; x++) {
                            double oldAlpha = !imageHeader.hasAlpha() ? 1.0D : ref != null ?
                                ref.getSample(colorChannels + info.alphaChannel, x, y) : 0.0D;
                            double newAlpha = !imageHeader.hasAlpha() ? 1.0D
                                : frame.getSample(colorChannels + info.alphaChannel, x, y);
                            double alpha = oldAlpha + newAlpha * (1.0D - oldAlpha);
                            if (info.clamp)
                                alpha = MathHelper.clamp(alpha, 0.0D, 1.0D);
                            double oldSample = ref != null ? ref.getSample(c, x, y) : 0.0D;
                            double newSample = frame.getSample(c, x, y);
                            canvas[c][y][x] = isAlpha ? alpha : oldSample + alpha * newSample;
                        }
                    }
                    break;
                default:
                    throw new InvalidBitstreamException("Illegal blend mode");
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
            if (!iccDistribution.validateFinalState())
                throw new InvalidBitstreamException("ICC Stream");
        }
        bitreader.zeroPadToByte();

        if (imageHeader.getPreviewHeader() != null) {
            Frame frame = new Frame(bitreader, imageHeader);
            frame.readHeader();
            frame.skipFrameData();
        }

        List<Frame> frames = new ArrayList<>();
        Frame[] reference = new Frame[4];
        FrameHeader header;

        do {
            Frame frame = new Frame(bitreader, imageHeader);
            frame.readHeader();
            frame.decodeFrame();
            frames.add(frame);
            header = frame.getFrameHeader();
        } while (!header.isLast);

        OpsinInverseMatrix matrix = null;
        if  (imageHeader.isXYBEncoded()) {
            CIEPrimaries prim = imageHeader.getColorEncoding().prim;
            CIEXY white = imageHeader.getColorEncoding().white;
            matrix = imageHeader.getOpsinInverseMatrix().getMatrix(prim, white);
        }

        Frame storage = new Frame(frames.get(0), false);

        for (Frame frame : frames) {
            header = frame.getFrameHeader();
            boolean save = (header.saveAsReference != 0 || header.duration == 0) && !header.isLast && header.type != FrameFlags.LF_FRAME;
            if (save && header.saveBeforeCT)
                reference[header.saveAsReference] = new Frame(frame);
            computePatches(reference, frame);
            frame.renderSplines();
            performColorTransforms(matrix, frame);
            if (header.type == FrameFlags.REGULAR_FRAME || header.type == FrameFlags.SKIP_PROGRESSIVE) {
                blendFrame(storage.getBuffer(), reference, frame);
            }
            if (save && !header.saveBeforeCT)
                reference[header.saveAsReference] = new Frame(storage);
        }

        return new JXLImage(storage.getBuffer(), imageHeader);
    }
}
