package com.traneptora.jxlatte;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.traneptora.jxlatte.bundle.ExtraChannelType;
import com.traneptora.jxlatte.bundle.ImageHeader;
import com.traneptora.jxlatte.color.ColorEncodingBundle;
import com.traneptora.jxlatte.color.ColorFlags;
import com.traneptora.jxlatte.color.OpsinInverseMatrix;
import com.traneptora.jxlatte.frame.BlendingInfo;
import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.FrameFlags;
import com.traneptora.jxlatte.frame.FrameHeader;
import com.traneptora.jxlatte.frame.features.Patch;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.Demuxer;
import com.traneptora.jxlatte.io.Loggers;
import com.traneptora.jxlatte.io.PushbackInputStream;
import com.traneptora.jxlatte.util.Dimension;
import com.traneptora.jxlatte.util.MathHelper;
import com.traneptora.jxlatte.util.Point;

public class JXLCodestreamDecoder {

    private static void copyToCanvas(float[][] canvas, Point start, Point off, Dimension size, float[][] frameBuffer) {
        for (int y = 0; y < size.height; y++)
            System.arraycopy(frameBuffer[y + off.y], off.x, canvas[y + start.y], start.x, size.width);
    }

    private static float[][] transposeBuffer(float[][] src, int orientation) {
        int srcHeight = src.length;
        int srcWidth = src[0].length;
        int srcH1 = srcHeight - 1;
        int srcW1 = srcWidth - 1;
        float[][] dest = orientation > 4 ? new float[srcWidth][srcHeight]
            : orientation > 1 ? new float[srcHeight][srcWidth] : null;
        switch (orientation) {
            case 1:
                return src;
            case 2:
                // flip horizontally
                for (int y = 0; y < srcHeight; y++) {
                    for (int x = 0; x < srcWidth; x++) {
                        dest[y][srcW1 - x] = src[y][x];
                    }
                }
                return dest;
            case 3:
                // rotate 180 degrees
                for (int y = 0; y < srcHeight; y++) {
                    for (int x = 0; x < srcWidth; x++) {
                        dest[srcH1 - y][srcW1 - x] = src[y][x];
                    }
                }
                return dest;
            case 4:
                // flip vertically
                for (int y = 0; y < srcHeight; y++) {
                    System.arraycopy(src[y], 0, dest[srcH1 - y], 0, srcWidth);
                }
                return dest;
            case 5:
                // transpose
                for (int y = 0; y < srcHeight; y++) {
                    for (int x = 0; x < srcWidth; x++) {
                        dest[x][y] = src[y][x];
                    }
                }
                return dest;
            case 6:
                // rotate clockwise
                for (int y = 0; y < srcHeight; y++) {
                    for (int x = 0; x < srcWidth; x++) {
                        dest[x][srcH1 - y] = src[y][x];
                    }
                }
                return dest;
            case 7:
                // skew transpose
                for (int y = 0; y < srcHeight; y++) {
                    for (int x = 0; x < srcWidth; x++) {
                        dest[srcW1 - x][srcH1 - y] = src[y][x];
                    }
                }
                return dest;
            case 8:
                // rotate counterclockwise
                for (int y = 0; y < srcHeight; y++) {
                    for (int x = 0; x < srcWidth; x++) {
                        dest[srcW1 - x][y] = src[y][x];
                    }
                }
                return dest;
            default:
                throw new IllegalStateException("Challenge complete how did we get here");
        }
    }

    private Bitreader bitreader;
    private PushbackInputStream in;
    private ImageHeader imageHeader;
    private JXLOptions options;
    private Demuxer demuxer;

    public JXLCodestreamDecoder(PushbackInputStream in, JXLOptions options, Demuxer demuxer) {
        this.in = in;
        this.bitreader = new Bitreader(in);
        this.options = options;
        this.demuxer = demuxer;
    }

    private void computePatches(float[][][][] references, Frame frame) throws InvalidBitstreamException {
        FrameHeader header = frame.getFrameHeader();
        float[][][] frameBuffer = frame.getBuffer();
        int colorChannels = imageHeader.getColorChannelCount();
        int extraChannels = imageHeader.getExtraChannelCount();
        Patch[] patches = frame.getLFGlobal().patches;
        for (int i = 0; i < patches.length; i++) {
            Patch patch = patches[i];
            if (patch.ref > 3)
                throw new InvalidBitstreamException("Patch out of range");
            float[][][] refBuffer = references[patch.ref];
            // technically you can reference a nonexistent frame
            // you wouldn't but it's not against the rules
            if (refBuffer == null)
                continue;
            Point lowerCorner = patch.bounds.computeLowerCorner();
            if (lowerCorner.y > refBuffer[0].length || lowerCorner.x > refBuffer[0][0].length)
                    throw new InvalidBitstreamException("Patch too large");
            for (int j = 0; j < patch.positions.length; j++) {
                int y0 = patch.positions[j].y;
                int x0 = patch.positions[j].x;
                if (y0 < 0 || x0 < 0)
                    throw new InvalidBitstreamException("Patch size out of bounds");
                if (patch.bounds.size.height + y0 > header.bounds.size.height ||
                        patch.bounds.size.width + x0 > header.bounds.size.width)
                    throw new InvalidBitstreamException("Patch size out of bounds");
                for (int d = 0; d < colorChannels + extraChannels; d++) {
                    int c = d < colorChannels ? 0 : d - colorChannels + 1;
                    BlendingInfo info = patch.blendingInfos[j][c];
                    if (info.mode == 0)
                        continue;
                    boolean premult = imageHeader.hasAlpha() &&
                        imageHeader.getExtraChannelInfo(info.alphaChannel).alphaAssociated;
                    boolean isAlpha = c > 0 && imageHeader.getExtraChannelInfo(c - 1).type == ExtraChannelType.ALPHA;
                    if (info.mode > 3 && header.upsampling > 1 && c > 0 &&
                            header.ecUpsampling[c - 1] << imageHeader.getExtraChannelInfo(c - 1).dimShift
                            != header.upsampling) {
                        throw new InvalidBitstreamException("Alpha channel upsampling mismatch during patches");
                    }
                    for (int y = 0; y < patch.bounds.size.height; y++) {
                        for (int x = 0; x < patch.bounds.size.width; x++) {
                            int oldY = y + y0;
                            int oldX = x + x0;
                            int newY = y + patch.bounds.origin.y;
                            int newX = x + patch.bounds.origin.x;
                            float oldSample = frameBuffer[d][oldY][oldX];
                            float newSample = refBuffer[d][newY][newX];
                            float alpha = 1.0f, newAlpha = 1.0f, oldAlpha = 1.0f;
                            if (info.mode > 3 && imageHeader.hasAlpha()) {
                                oldAlpha = frameBuffer[colorChannels + info.alphaChannel][oldY][oldX];
                                newAlpha = refBuffer[colorChannels + info.alphaChannel][newY][newX];
                                if (info.clamp)
                                    newAlpha = MathHelper.clamp(newAlpha, 0.0f, 1.0f);
                                if (info.mode < 6 || !isAlpha || !premult)
                                    alpha = oldAlpha + newAlpha * (1 - oldAlpha);
                            }
                            float sample;
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
                                    sample = isAlpha ? newAlpha : oldSample + alpha * newSample;
                                    break;
                                case 7:
                                    sample = isAlpha ? oldAlpha : newSample + alpha * oldSample;
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
        float[][][] frameBuffer = frame.getBuffer();
        if (matrix != null)
            matrix.invertXYB(frameBuffer, imageHeader.getToneMapping().intensityTarget);

        if (frame.getFrameHeader().doYCbCr) {
            Dimension paddedSize = frame.getPaddedFrameSize();
            for (int y = 0; y < paddedSize.height; y++) {
                for (int x = 0; x < paddedSize.width; x++) {
                    float cb = frameBuffer[0][y][x];
                    float yh = frameBuffer[1][y][x] + 0.50196078431372549019f;
                    float cr = frameBuffer[2][y][x];
                    frameBuffer[0][y][x] = yh + 1.402f * cr;
                    frameBuffer[1][y][x] = yh - 0.34413628620102214650f * cb - 0.71413628620102214650f * cr;
                    frameBuffer[2][y][x] = yh + 1.772f * cb;
                }
            }
        }
    }

    public void blendFrame(float[][][] canvas, float[][][][] reference, Frame frame)
            throws InvalidBitstreamException {
        int height = imageHeader.getSize().height;
        int width = imageHeader.getSize().width;
        FrameHeader header = frame.getFrameHeader();
        int frameStartY = header.bounds.origin.y < 0 ? 0 : header.bounds.origin.y;
        int frameStartX = header.bounds.origin.x < 0 ? 0 : header.bounds.origin.x;
        Point lowerCorner = header.bounds.computeLowerCorner();
        int frameHeight = Math.min(lowerCorner.y, height);
        int frameWidth = Math.min(lowerCorner.x, width);
        int frameColors = frame.getColorChannelCount();
        int imageColors = imageHeader.getColorChannelCount();
        boolean hasAlpha = imageHeader.hasAlpha();
        for (int c = 0; c < canvas.length; c++) {
            int frameC = frameColors != imageColors ? (c == 0 ? 1 : c + 2) : c;
            float[][] frameBuffer = frame.getBuffer()[frameC];
            BlendingInfo info;
            if (frameC < frameColors)
                info = frame.getFrameHeader().blendingInfo;
            else
                info = frame.getFrameHeader().ecBlendingInfo[frameC - frameColors];
            boolean isAlpha = c >= imageColors &&
                imageHeader.getExtraChannelInfo(c - imageColors).type == ExtraChannelType.ALPHA;
            boolean premult = hasAlpha && imageHeader.getExtraChannelInfo(info.alphaChannel).alphaAssociated;
            float[][][] refBuffer = reference[info.source];
            if (info.mode == FrameFlags.BLEND_REPLACE || refBuffer == null && info.mode == FrameFlags.BLEND_ADD) {
                int offY = frameStartY - header.bounds.origin.y;
                int offX = frameStartX - header.bounds.origin.x;
                copyToCanvas(canvas[c], new Point(frameStartY, frameStartX), new Point(offY, offX),
                    new Dimension(frameHeight, frameWidth), frameBuffer);
                continue;
            }
            float[][] ref = refBuffer[c];
            switch (info.mode) {
                case FrameFlags.BLEND_ADD:
                    for (int y = 0; y < frameHeight; y++) {
                        int cy = y + frameStartY;
                        for (int x = 0; x < frameWidth; x++) {
                            int cx = x + frameStartX;
                            canvas[c][cy][cx] = ref[cy][cx] + frameBuffer[y][x];
                        }
                    }
                    break;
                case FrameFlags.BLEND_MULT:
                    for (int y = 0; y < frameHeight; y++) {
                        int cy = y + frameStartY;
                        if (ref != null) {
                            for (int x = 0; x < frameWidth; x++) {
                                int cx = x + frameStartX;
                                float newSample = frameBuffer[y][x];
                                if (info.clamp)
                                    newSample = MathHelper.clamp(newSample, 0.0f, 1.0f);
                                canvas[c][cy][cx] = newSample * ref[cy][cx];
                            }
                        } else {
                            Arrays.fill(canvas[c][cy], frameStartX, frameWidth, 0f);
                        }
                    }
                    break;
                case FrameFlags.BLEND_BLEND:
                    for (int cy = frameStartY; cy < frameHeight + frameStartY; cy++) {
                        for (int cx = frameStartX; cx < frameWidth + frameStartX; cx++) {
                            float oldAlpha = !hasAlpha ? 1.0f : ref != null ?
                                refBuffer[imageColors + info.alphaChannel][cy][cx] : 0.0f;
                            float newAlpha = !hasAlpha ? 1.0f :
                                frame.getImageSample(frameColors + info.alphaChannel, cy, cx);
                            if (info.clamp)
                                newAlpha = MathHelper.clamp(newAlpha, 0.0f, 1.0f);
                            float alpha = 1.0f;
                            float oldSample = ref != null ? ref[cy][cx] : 0.0f;
                            float newSample = frame.getImageSample(frameC, cy, cx);
                            if (isAlpha || hasAlpha && !premult)
                                alpha = oldAlpha + newAlpha * (1 - oldAlpha);
                            canvas[c][cy][cx] = isAlpha ? alpha : (!hasAlpha || premult) ?
                                newSample + oldSample * (1.0f - newAlpha) :
                                (newSample * newAlpha + oldSample * oldAlpha * (1.0f - newAlpha)) / alpha;
                        }
                    }
                    break;
                case FrameFlags.BLEND_MULADD:
                    for (int cy = frameStartY; cy < frameHeight + frameStartY; cy++) {
                        for (int cx = frameStartX; cx < frameWidth + frameStartX; cx++) {
                            float oldAlpha = !hasAlpha ? 1.0f : ref != null ?
                                refBuffer[imageColors + info.alphaChannel][cy][cx] : 0.0f;
                            float newAlpha = !hasAlpha ? 1.0f :
                                frame.getImageSample(frameColors + info.alphaChannel, cy, cx);
                            if (info.clamp)
                                newAlpha = MathHelper.clamp(newAlpha, 0.0f, 1.0f);
                            float oldSample = ref != null ? ref[cy][cx] : 0.0f;
                            float newSample = frame.getImageSample(frameC, cy, cx);
                            canvas[c][cy][cx] = isAlpha ? oldAlpha : oldSample + newAlpha * newSample;
                        }
                    }
                    break;
                default:
                    throw new InvalidBitstreamException("Illegal blend mode");
            }
        }
    }

    public boolean atEnd() throws IOException {
        return bitreader != null && bitreader.atEnd();
    }

    public JXLImage decode() throws IOException {
        return decode(new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8)));
    }

    public JXLImage decode(PrintWriter err) throws IOException {
        if (atEnd())
            return null;
        Loggers loggers = new Loggers(options, err);
        bitreader.showBits(16); // force the level to be populated
        int level = demuxer.getLevel();
        this.imageHeader = ImageHeader.read(loggers, bitreader, level);
        loggers.log(Loggers.LOG_INFO, "Image: %s", options.input != null ? options.input : "<stdin>");
        loggers.log(Loggers.LOG_INFO, "    Level: %d", level);
        Dimension size = imageHeader.getSize();
        loggers.log(Loggers.LOG_INFO, "    Size: %dx%d", size.width, size.height);
        boolean gray = imageHeader.getColorChannelCount() < 3;
        boolean alpha = imageHeader.hasAlpha();
        loggers.log(Loggers.LOG_INFO, "    Pixel Format: %s",
            gray ? (alpha ? "Gray + Alpha" : "Grayscale") : (alpha ? "RGBA" : "RGB"));
        loggers.log(Loggers.LOG_INFO, "    Bit Depth: %d", imageHeader.getBitDepthHeader().bitsPerSample);
        loggers.log(Loggers.LOG_VERBOSE, "    Extra Channels: %d", imageHeader.getExtraChannelCount());
        loggers.log(Loggers.LOG_VERBOSE, "    XYB Encoded: %b", imageHeader.isXYBEncoded());
        ColorEncodingBundle ce = imageHeader.getColorEncoding();
        if (!gray)
            loggers.log(Loggers.LOG_VERBOSE, "    Primaries: %s", ColorFlags.primariesToString(ce.primaries));
        loggers.log(Loggers.LOG_VERBOSE, "    White Point: %s", ColorFlags.whitePointToString(ce.whitePoint));
        loggers.log(Loggers.LOG_VERBOSE, "    Transfer Function: %s", ColorFlags.transferToString(ce.tf));
        if (imageHeader.getAnimationHeader() != null)
            loggers.log(Loggers.LOG_INFO, "    Animated: true");

        if (imageHeader.getPreviewSize() != null) {
            JXLOptions previewOptions = new JXLOptions(options);
            previewOptions.parseOnly = true;
            Frame frame = new Frame(bitreader, imageHeader, loggers, previewOptions);
            frame.readFrameHeader();
            frame.readTOC();
            frame.skipFrameData();
        }

        int frameCount = 0;
        float[][][][] reference = new float[4][][][];
        FrameHeader header;
        // last one is always null, avoids ugly branch later
        float[][][][] lfBuffer = new float[5][][][];

        OpsinInverseMatrix matrix = null;
        if (imageHeader.isXYBEncoded()) {
            ColorEncodingBundle bundle = imageHeader.getColorEncoding();
            matrix = imageHeader.getOpsinInverseMatrix().getMatrix(bundle.prim, bundle.white);
        }

        float[][][] canvas = null;
        if (!options.parseOnly)
            canvas = new float[imageHeader.getColorChannelCount() + imageHeader.getExtraChannelCount()]
                [imageHeader.getSize().height][imageHeader.getSize().width];

        long invisibleFrames = 0;
        long visibleFrames = 0;

        do {
            Frame frame = new Frame(bitreader, imageHeader, loggers, options);
            header = frame.readFrameHeader();
            if (frameCount++ == 0) {
                loggers.log(Loggers.LOG_INFO, "    Lossless: %s",
                    header.encoding == FrameFlags.VARDCT || imageHeader.isXYBEncoded() ? "No" : "Possibly");
            }
            frame.printDebugInfo();
            loggers.log(Loggers.LOG_TRACE, "%s", header);
            frame.readTOC();
            if (lfBuffer[header.lfLevel] == null && (header.flags & FrameFlags.USE_LF_FRAME) != 0)
                throw new InvalidBitstreamException("LF Level too large");
            if (options.parseOnly) {
                frame.skipFrameData();
                continue;
            }
            frame.decodeFrame(lfBuffer[header.lfLevel]);
            if (header.lfLevel > 0)
                lfBuffer[header.lfLevel - 1] = frame.getBuffer();
            if (header.type == FrameFlags.LF_FRAME)
                continue;
            boolean save = (header.saveAsReference != 0 || header.duration == 0)
                && !header.isLast && header.type != FrameFlags.LF_FRAME;
            if (frame.isVisible()) {
                visibleFrames++;
                invisibleFrames = 0;
            } else {
                invisibleFrames++;
            }
            frame.initializeNoise((visibleFrames << 32) | invisibleFrames);
            frame.upsample();
            if (save && header.saveBeforeCT)
                reference[header.saveAsReference] = MathHelper.deepCopyOf(frame.getBuffer());
            computePatches(reference, frame);
            frame.renderSplines();
            frame.synthesizeNoise();
            performColorTransforms(matrix, frame);
            if (header.encoding == FrameFlags.VARDCT && options.renderVarblocks)
                frame.drawVarblocks();
            if (header.type == FrameFlags.REGULAR_FRAME || header.type == FrameFlags.SKIP_PROGRESSIVE) {
                boolean found = false;
                for (int i = 0; i < 4; i++) {
                    if (reference[i] == canvas && i != header.saveAsReference) {
                        found = true;
                        break;
                    }
                }
                if (found)
                    canvas = MathHelper.deepCopyOf(canvas);
                blendFrame(canvas, reference, frame);
            }
            if (save && !header.saveBeforeCT)
                reference[header.saveAsReference] = canvas;
        } while (!header.isLast);

        bitreader.zeroPadToByte();
        byte[] drain = bitreader.drainCache();
        if (drain != null)
            demuxer.pushBack(drain);
        while ((drain = in.drain()) != null)
            demuxer.pushBack(drain);

        if (options.parseOnly)
            return null;

        int orientation = imageHeader.getOrientation();

        float[][][] orientedCanvas = new float[canvas.length][][];

        for (int i = 0; i < orientedCanvas.length; i++)
            orientedCanvas[i] = transposeBuffer(canvas[i], orientation);

        JXLImage image = new JXLImage(orientedCanvas, imageHeader);

        return image;
    }
}
