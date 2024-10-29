package com.traneptora.jxlatte;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import com.traneptora.jxlatte.bundle.BlendingInfo;
import com.traneptora.jxlatte.bundle.ExtraChannelType;
import com.traneptora.jxlatte.bundle.ImageHeader;
import com.traneptora.jxlatte.color.ColorEncodingBundle;
import com.traneptora.jxlatte.color.ColorFlags;
import com.traneptora.jxlatte.color.OpsinInverseMatrix;
import com.traneptora.jxlatte.frame.Frame;
import com.traneptora.jxlatte.frame.FrameFlags;
import com.traneptora.jxlatte.frame.FrameHeader;
import com.traneptora.jxlatte.frame.features.Patch;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.Demuxer;
import com.traneptora.jxlatte.io.InvalidBitstreamException;
import com.traneptora.jxlatte.io.Loggers;
import com.traneptora.jxlatte.io.PushbackInputStream;
import com.traneptora.jxlatte.util.Dimension;
import com.traneptora.jxlatte.util.ImageBuffer;
import com.traneptora.jxlatte.util.Point;

public class JXLCodestreamDecoder {

    private static void copyToCanvas(ImageBuffer canvas, Point start, Point off,
            Dimension size, ImageBuffer frameBuffer) {
        Object[] canvasB = canvas.isInt() ? canvas.getIntBuffer() : canvas.getFloatBuffer();
        Object[] frameB = frameBuffer.isInt() ? frameBuffer.getIntBuffer() : frameBuffer.getFloatBuffer();
        for (int y = 0; y < size.height; y++) {
            System.arraycopy(frameB[y + off.y], off.x, canvasB[y + start.y], start.x, size.width);
        }
    }

    private static float[][] transposeBufferFloat(float[][] src, int orientation) {
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

    private static int[][] transposeBufferInt(int[][] src, int orientation) {
        int srcHeight = src.length;
        int srcWidth = src[0].length;
        int srcH1 = srcHeight - 1;
        int srcW1 = srcWidth - 1;
        int[][] dest = orientation > 4 ? new int[srcWidth][srcHeight]
            : orientation > 1 ? new int[srcHeight][srcWidth] : null;
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

    private static ImageBuffer transposeBuffer(ImageBuffer src, int orientation) {
        if (src.isInt())
            return new ImageBuffer(transposeBufferInt(src.getIntBuffer(), orientation));
        else
            return new ImageBuffer(transposeBufferFloat(src.getFloatBuffer(), orientation));
    }

    private Bitreader bitreader;
    private PushbackInputStream in;
    private ImageHeader imageHeader;
    private JXLOptions options;
    private Demuxer demuxer;
    private boolean skippedPreview = false;
    private long visibleFrames = 0L;
    private long invisibleFrames = 0L;
    private int totalFrames = 0;
    private ImageBuffer[][] reference = new ImageBuffer[4][];
    private ImageBuffer[][] lfBuffer = new ImageBuffer[5][];
    private ImageBuffer[] canvas;

    public JXLCodestreamDecoder(PushbackInputStream in, JXLOptions options, Demuxer demuxer) {
        this.in = in;
        this.bitreader = new Bitreader(in);
        this.options = options;
        this.demuxer = demuxer;
    }

    private void computePatches(Frame frame) throws InvalidBitstreamException {
        FrameHeader header = frame.getFrameHeader();
        ImageBuffer[] frameBuffer = frame.getBuffer();
        int colorChannels = imageHeader.getColorChannelCount();
        int extraChannels = imageHeader.getExtraChannelCount();
        Patch[] patches = frame.getLFGlobal().patches;
        boolean hasAlpha = imageHeader.hasAlpha();
        for (int i = 0; i < patches.length; i++) {
            Patch patch = patches[i];
            if (patch.ref > 3)
                throw new InvalidBitstreamException("Patch out of range");
            ImageBuffer[] refBuffer = reference[patch.ref];
            // technically you can reference a nonexistent frame
            // you wouldn't but it's not against the rules
            if (refBuffer == null)
                continue;
            Point lowerCorner = patch.bounds.computeLowerCorner();
            if (lowerCorner.y > refBuffer[0].height || lowerCorner.x > refBuffer[0].width)
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
                    boolean toFloat = true;
                    switch (info.mode) {
                        case 1:
                            if (refBuffer[d].isInt() && frameBuffer[d].isInt()) {
                                int[][] refBufferI = refBuffer[d].getIntBuffer();
                                int[][] frameBufferI = frameBuffer[d].getIntBuffer();
                                for (int y = 0; y < patch.bounds.size.height; y++) {
                                    System.arraycopy(refBufferI[y0 + y], x0, frameBufferI[y + patch.bounds.origin.y],
                                        patch.bounds.origin.x, patch.bounds.size.width);
                                }
                                toFloat = false;
                            }
                            break;
                        case 2:
                            if (refBuffer[d].isInt() && frameBuffer[d].isInt()) {
                                int[][] refBufferI, frameBufferI;
                                refBufferI = refBuffer[d].getIntBuffer();
                                frameBufferI = frameBuffer[d].getIntBuffer();
                                for (int y = 0; y < patch.bounds.size.height; y++) {
                                    for (int x = 0; x <  patch.bounds.size.width; x++) {
                                        frameBufferI[y0 + y][x0 + x] += refBufferI[patch.bounds.origin.y + y][patch.bounds.origin.x + x];
                                    }
                                }
                                toFloat = false;
                            }
                            break;
                    }
                    if (toFloat) {
                        int depth = c == 0 ? imageHeader.getBitDepthHeader().bitsPerSample :
                            imageHeader.getExtraChannelInfo(c - 1).bitDepth.bitsPerSample;
                        int max = ~(~0 << depth);
                        refBuffer[d].castToFloatIfInt(max);
                        frameBuffer[d].castToFloatIfInt(max);
                    }
                    float[][] refBufferF, frameBufferF;
                    if (toFloat) {
                        refBufferF = refBuffer[d].getFloatBuffer();
                        frameBufferF = frameBuffer[d].getFloatBuffer();
                    } else {
                        refBufferF = null;
                        frameBufferF = null;
                    }
                    float[][] alphaBufferOld, alphaBufferNew;
                    if (info.mode > 3 && hasAlpha) {
                        int depth = imageHeader.getExtraChannelInfo(info.alphaChannel).bitDepth.bitsPerSample;
                        frameBuffer[colorChannels + info.alphaChannel].castToFloatIfInt(~(~0 << depth));
                        refBuffer[colorChannels + info.alphaChannel].castToFloatIfInt(~(~0 << depth));
                        alphaBufferOld = frameBuffer[colorChannels + info.alphaChannel].getFloatBuffer();
                        alphaBufferNew = refBuffer[colorChannels + info.alphaChannel].getFloatBuffer();
                    } else {
                        alphaBufferOld = null;
                        alphaBufferNew = null;
                    }
                    switch (info.mode) {
                        case 1:
                            if (!toFloat)
                                break;
                            for (int y = 0; y < patch.bounds.size.height; y++) {
                                System.arraycopy(refBufferF[y0 + y], x0, frameBufferF[y + patch.bounds.origin.y],
                                    patch.bounds.origin.x, patch.bounds.size.width);
                            }
                            break;
                        case 2:
                            if (!toFloat)
                                break;
                            for (int y = 0; y < patch.bounds.size.height; y++) {
                                for (int x = 0; x < patch.bounds.size.width; x++) {
                                    frameBufferF[y0 + y][x0 + x] += refBufferF[patch.bounds.origin.y + y][patch.bounds.origin.x + x];
                                }
                            }
                            break;
                        case 3:
                            for (int y = 0; y < patch.bounds.size.height; y++) {
                                for (int x = 0; x < patch.bounds.size.width; x++) {
                                    frameBufferF[y0 + y][x0 + x] *= refBufferF[patch.bounds.origin.y + y][patch.bounds.origin.x + x];
                                }
                            }
                            break;
                        case 4:
                            if (isAlpha) {
                                for (int y = 0; y < patch.bounds.size.height; y++) {
                                    int newY = y + patch.bounds.origin.y;
                                    int oldY = y + y0;
                                    for (int x = 0; x < patch.bounds.size.width; x++) {
                                        int oldX = x + x0;
                                        int newX = x + patch.bounds.origin.x;
                                        float newAlpha = alphaBufferNew[newY][newX];
                                        if (info.clamp)
                                            newAlpha = newAlpha < 0.0f ? 0.0f : newAlpha > 1.0f ? 1.0f : newAlpha;
                                        frameBufferF[oldY][oldX] = alphaBufferOld[oldY][oldX] + newAlpha * (1 - alphaBufferOld[oldY][oldX]);
                                    }
                                }
                            } else if (premult) {
                                for (int y = 0; y < patch.bounds.size.height; y++) {
                                    int newY = y + patch.bounds.origin.y;
                                    int oldY = y + y0;
                                    for (int x = 0; x < patch.bounds.size.width; x++) {
                                        int oldX = x + x0;
                                        int newX = x + patch.bounds.origin.x;
                                        float newAlpha = alphaBufferNew[newY][newX];
                                        if (info.clamp)
                                            newAlpha = newAlpha < 0.0f ? 0.0f : newAlpha > 1.0f ? 1.0f : newAlpha;
                                        frameBufferF[oldY][oldX] = refBufferF[newY][newX] + frameBufferF[oldY][oldX] * (1 - newAlpha);
                                    }
                                }
                            } else {
                                for (int y = 0; y < patch.bounds.size.height; y++) {
                                    int newY = y + patch.bounds.origin.y;
                                    int oldY = y + y0;
                                    for (int x = 0; x < patch.bounds.size.width; x++) {
                                        int oldX = x + x0;
                                        int newX = x + patch.bounds.origin.x;
                                        float oldAlpha = hasAlpha ? alphaBufferOld[oldY][oldX] : 1.0f;
                                        float newAlpha = hasAlpha ? alphaBufferNew[newY][newX] : 1.0f;
                                        if (info.clamp)
                                            newAlpha = newAlpha < 0.0f ? 0.0f : newAlpha > 1.0f ? 1.0f : newAlpha;
                                        float alpha = oldAlpha + newAlpha * (1.0f - oldAlpha);
                                        frameBufferF[oldY][oldX] = (refBufferF[newY][newX] * newAlpha +
                                            frameBufferF[oldY][oldX] * oldAlpha * (1.0f - newAlpha)) / alpha;
                                    }
                                }
                            }
                            break;
                        case 5:
                            if (isAlpha) {
                                for (int y = 0; y < patch.bounds.size.height; y++) {
                                    int newY = y + patch.bounds.origin.y;
                                    int oldY = y + y0;
                                    for (int x = 0; x < patch.bounds.size.width; x++) {
                                        int oldX = x + x0;
                                        int newX = x + patch.bounds.origin.x;
                                        frameBufferF[oldY][oldX] = alphaBufferOld[oldY][oldX] + alphaBufferNew[newY][newX] * (1 - alphaBufferOld[oldY][oldX]);
                                    }
                                }
                            } else if (premult) {
                                for (int y = 0; y < patch.bounds.size.height; y++) {
                                    int newY = y + patch.bounds.origin.y;
                                    int oldY = y + y0;
                                    for (int x = 0; x < patch.bounds.size.width; x++) {
                                        int oldX = x + x0;
                                        int newX = x + patch.bounds.origin.x;
                                        float newAlpha = alphaBufferNew[newY][newX];
                                        if (info.clamp)
                                            newAlpha = newAlpha < 0.0f ? 0.0f : newAlpha > 1.0f ? 1.0f : newAlpha;
                                        frameBufferF[oldY][oldX] = frameBufferF[oldY][oldX] + refBufferF[newY][newX] * (1 - newAlpha);
                                    }
                                }
                            } else {
                                for (int y = 0; y < patch.bounds.size.height; y++) {
                                    int newY = y + patch.bounds.origin.y;
                                    int oldY = y + y0;
                                    for (int x = 0; x < patch.bounds.size.width; x++) {
                                        int oldX = x + x0;
                                        int newX = x + patch.bounds.origin.x;
                                        float oldAlpha = hasAlpha ? alphaBufferOld[oldY][oldX] : 1.0f;
                                        float newAlpha = hasAlpha ? alphaBufferNew[newY][newX] : 1.0f;
                                        float alpha = oldAlpha + newAlpha * (1.0f - oldAlpha);
                                        frameBufferF[oldY][oldX] = (frameBufferF[oldY][oldX] * newAlpha +
                                            refBufferF[newY][newX] * oldAlpha * (1.0f - newAlpha)) / alpha;
                                    }
                                }
                            }
                            break;
                        case 6:
                            if (isAlpha) {
                                for (int y = 0; y < patch.bounds.size.height; y++) {
                                    int newY = y + patch.bounds.origin.y;
                                    int oldY = y + y0;
                                    for (int x = 0; x < patch.bounds.size.width; x++) {
                                        int oldX = x + x0;
                                        int newX = x + patch.bounds.origin.x;
                                        float newAlpha = alphaBufferNew[newY][newX];
                                        if (info.clamp)
                                            newAlpha = newAlpha < 0.0f ? 0.0f : newAlpha > 1.0f ? 1.0f : newAlpha;
                                        frameBufferF[oldY][oldX] = hasAlpha ? newAlpha : 1.0f;
                                    }
                                }
                            } else {
                                for (int y = 0; y < patch.bounds.size.height; y++) {
                                    int newY = y + patch.bounds.origin.y;
                                    int oldY = y + y0;
                                    for (int x = 0; x < patch.bounds.size.width; x++) {
                                        int oldX = x + x0;
                                        int newX = x + patch.bounds.origin.x;
                                        float oldAlpha = hasAlpha ? alphaBufferOld[oldY][oldX] : 1.0f;
                                        float newAlpha = hasAlpha ? alphaBufferNew[newY][newX] : 1.0f;
                                        if (info.clamp)
                                            newAlpha = newAlpha < 0.0f ? 0.0f : newAlpha > 1.0f ? 1.0f : newAlpha;
                                        float alpha = oldAlpha + newAlpha * (1.0f - oldAlpha);
                                        frameBufferF[oldY][oldX] += alpha * refBufferF[newY][newX];
                                    }
                                }
                            }
                            break;
                        case 7:
                            if (isAlpha) {
                                for (int y = 0; y < patch.bounds.size.height; y++) {
                                    int oldY = y + y0;
                                    for (int x = 0; x < patch.bounds.size.width; x++) {
                                        int oldX = x + x0;
                                        frameBufferF[oldY][oldX] = hasAlpha ? alphaBufferOld[oldY][oldX] : 1.0f;
                                    }
                                }
                            } else {
                                for (int y = 0; y < patch.bounds.size.height; y++) {
                                    int newY = y + patch.bounds.origin.y;
                                    int oldY = y + y0;
                                    for (int x = 0; x < patch.bounds.size.width; x++) {
                                        int oldX = x + x0;
                                        int newX = x + patch.bounds.origin.x;
                                        float oldAlpha = hasAlpha ? alphaBufferOld[oldY][oldX] : 1.0f;
                                        float newAlpha = hasAlpha ? alphaBufferNew[newY][newX] : 1.0f;
                                        if (info.clamp)
                                            newAlpha = newAlpha < 0.0f ? 0.0f : newAlpha > 1.0f ? 1.0f : newAlpha;
                                        float alpha = oldAlpha + newAlpha * (1.0f - oldAlpha);
                                        frameBufferF[oldY][oldX] = refBufferF[newY][newX] + alpha * frameBufferF[oldY][oldX];
                                    }
                                }
                            }
                            break;
                        default:
                            throw new IllegalStateException("Challenge complete, how did we get here");
                    }
                }
            }
        }
    }

    public void performColorTransforms(OpsinInverseMatrix matrix, Frame frame) {
        if (matrix == null && !frame.getFrameHeader().doYCbCr)
            return;
        ImageBuffer[] buffer = frame.getBuffer();
        float[][][] buffers = new float[3][][];
        int depth = imageHeader.getBitDepthHeader().bitsPerSample;
        for (int c = 0; c < 3; c++) {
            buffer[c].castToFloatIfInt(~(~0 << depth));
            buffers[c] = buffer[c].getFloatBuffer();
        }

        if (matrix != null)
            matrix.invertXYB(buffers, imageHeader.getToneMapping().intensityTarget);

        if (frame.getFrameHeader().doYCbCr) {
            Dimension paddedSize = frame.getPaddedFrameSize();
            for (int y = 0; y < paddedSize.height; y++) {
                for (int x = 0; x < paddedSize.width; x++) {
                    float cb = buffers[0][y][x];
                    float yh = buffers[1][y][x] + 0.50196078431372549019f;
                    float cr = buffers[2][y][x];
                    buffers[0][y][x] = yh + 1.402f * cr;
                    buffers[1][y][x] = yh - 0.34413628620102214650f * cb - 0.71413628620102214650f * cr;
                    buffers[2][y][x] = yh + 1.772f * cb;
                }
            }
        }
    }

    public void blendFrame(ImageBuffer[] canvas, Frame frame)
            throws InvalidBitstreamException {
        Dimension imageSize = imageHeader.getSize();
        FrameHeader header = frame.getFrameHeader();
        int frameStartY = header.bounds.origin.y < 0 ? 0 : header.bounds.origin.y;
        int frameStartX = header.bounds.origin.x < 0 ? 0 : header.bounds.origin.x;
        int frameOffsetY = frameStartY - header.bounds.origin.y;
        int frameOffsetX = frameStartX - header.bounds.origin.x;
        Point lowerCorner = header.bounds.computeLowerCorner();
        int frameHeight = Math.min(lowerCorner.y, imageSize.height) - frameStartY;
        int frameWidth = Math.min(lowerCorner.x, imageSize.width) - frameStartX;
        int frameColors = frame.getColorChannelCount();
        int imageColors = imageHeader.getColorChannelCount();
        boolean hasAlpha = imageHeader.hasAlpha();
        ImageBuffer[] frameBuffers = frame.getBuffer();
        for (int c = 0; c < canvas.length; c++) {
            int frameC = frameColors != imageColors ? (c == 0 ? 1 : c + 2) : c;
            ImageBuffer frameBuffer = frameBuffers[frameC];
            BlendingInfo info;
            if (frameC < frameColors)
                info = header.blendingInfo;
            else
                info = header.ecBlendingInfo[frameC - frameColors];
            boolean isAlpha = c >= imageColors &&
                imageHeader.getExtraChannelInfo(c - imageColors).type == ExtraChannelType.ALPHA;
            boolean premult = hasAlpha && imageHeader.getExtraChannelInfo(info.alphaChannel).alphaAssociated;
            ImageBuffer[] refBuffer = reference[info.source];
            if (canvas[c].getType() != frameBuffer.getType()) {
                int depthCanvas = (c >= imageColors ? imageHeader.getExtraChannelInfo(c - imageColors)
                    .bitDepth : imageHeader.getBitDepthHeader()).bitsPerSample;
                int depthFrame = (frameC >= frameColors ? imageHeader.getExtraChannelInfo(frameC - frameColors)
                    .bitDepth : imageHeader.getBitDepthHeader()).bitsPerSample;
                frameBuffer.castToFloatIfInt(~(~0 << depthFrame));
                canvas[c].castToFloatIfInt(~(~0 << depthCanvas));
            }
            if (info.mode == FrameFlags.BLEND_REPLACE || refBuffer == null && info.mode == FrameFlags.BLEND_ADD) {
                int offY = frameStartY - header.bounds.origin.y;
                int offX = frameStartX - header.bounds.origin.x;
                copyToCanvas(canvas[c], new Point(frameStartY, frameStartX), new Point(offY, offX),
                    new Dimension(frameHeight, frameWidth), frameBuffer);
                continue;
            }
            if (refBuffer[c] == null)
                refBuffer[c] = new ImageBuffer(frameBuffer.getType(), canvas[c].height, canvas[c].width);
            ImageBuffer ref = refBuffer[c];
            if (hasAlpha && (info.mode == FrameFlags.BLEND_BLEND || info.mode == FrameFlags.BLEND_MULADD)) {
                int depth = imageHeader.getExtraChannelInfo(info.alphaChannel).bitDepth.bitsPerSample;
                if (refBuffer[imageColors + info.alphaChannel] == null) {
                    refBuffer[imageColors + info.alphaChannel] = new ImageBuffer(ImageBuffer.TYPE_FLOAT,
                        canvas[c].height, canvas[c].width);
                }
                if (!refBuffer[imageColors + info.alphaChannel].isFloat())
                    refBuffer[imageColors + info.alphaChannel].castToFloatIfInt(~(~0 << depth));
                if (!frameBuffers[frameColors + info.alphaChannel].isFloat())
                    frameBuffers[frameColors + info.alphaChannel].castToFloatIfInt(~(~0 << depth));
            }
            if (ref.getType() != frameBuffer.getType() || info.mode != FrameFlags.BLEND_ADD) {
                int depthCanvas = (c >= imageColors ? imageHeader.getExtraChannelInfo(c - imageColors)
                    .bitDepth : imageHeader.getBitDepthHeader()).bitsPerSample;
                int depthFrame = (frameC >= imageColors ? imageHeader.getExtraChannelInfo(frameC - imageColors)
                    .bitDepth : imageHeader.getBitDepthHeader()).bitsPerSample;
                frameBuffer.castToFloatIfInt(~(~0 << depthFrame));
                canvas[c].castToFloatIfInt(~(~0 << depthCanvas));
                ref.castToFloatIfInt(~(~0 << depthCanvas));
            }
            float[][] cf, rf, ff, oaf, naf;
            if (info.mode != FrameFlags.BLEND_ADD || frameBuffer.isFloat()) {
                cf = canvas[c].getFloatBuffer();
                rf = ref.getFloatBuffer();
                ff = frameBuffer.getFloatBuffer();
            } else {
                cf = null;
                rf = null;
                ff = null;
            }
            switch (info.mode) {
                case FrameFlags.BLEND_ADD:
                    if (frameBuffer.isInt()) {
                        int[][] ci = canvas[c].getIntBuffer();
                        int[][] ri = ref.getIntBuffer();
                        int[][] fi = frameBuffer.getIntBuffer();
                        for (int y = 0; y < frameHeight; y++) {
                            int cy = y + frameStartY;
                            int fy = y + frameOffsetY;
                            for (int x = 0; x < frameWidth; x++) {
                                int cx = x + frameStartX;
                                int fx = x + frameOffsetX;
                                ci[cy][cx] = ri[cy][cx] + fi[fy][fx];
                            }
                        }
                    } else {
                        for (int y = 0; y < frameHeight; y++) {
                            int cy = y + frameStartY;
                            int fy = y + frameOffsetY;
                            for (int x = 0; x < frameWidth; x++) {
                                int cx = x + frameStartX;
                                int fx = x + frameOffsetX;
                                cf[cy][cx] = rf[cy][cx] + ff[fy][fx];
                            }
                        }
                    }
                    break;
                case FrameFlags.BLEND_MULT:
                    for (int y = 0; y < frameHeight; y++) {
                        int cy = y + frameStartY;
                        int fy = y + frameOffsetY;
                        for (int x = 0; x < frameWidth; x++) {
                            int cx = x + frameStartX;
                            int fx = x + frameOffsetX;
                            float newSample = ff[fy][fx];
                            if (info.clamp)
                                newSample = newSample < 0.0f ? 0.0f : newSample > 1.0f ? 1.0f : newSample;
                            cf[cy][cx] = newSample * rf[cy][cx];
                        }
                    }
                    break;
                case FrameFlags.BLEND_BLEND:
                    oaf = hasAlpha ? refBuffer[imageColors + info.alphaChannel].getFloatBuffer() : null;
                    naf = hasAlpha ? frameBuffers[frameColors + info.alphaChannel].getFloatBuffer() : null;
                    for (int y = 0; y < frameHeight; y++) {
                        int cy = y + frameStartY;
                        int fy = y + frameOffsetY;
                        for (int x = 0; x < frameWidth; x++) {
                            int cx = x + frameStartX;
                            int fx = x + frameOffsetX;
                            float oldAlpha = hasAlpha ? oaf[cy][cx] : 1.0f;
                            float newAlpha = hasAlpha ? naf[fy][fx] : 1.0f;
                            if (info.clamp)
                                newAlpha = newAlpha < 0.0f ? 0.0f : newAlpha > 1.0f ? 1.0f : newAlpha;
                            float alpha = 1.0f;
                            float oldSample = rf[cy][cx];
                            float newSample = ff[fy][fx];
                            if (isAlpha || hasAlpha && !premult)
                                alpha = oldAlpha + newAlpha * (1 - oldAlpha);
                            cf[cy][cx] = isAlpha ? alpha : (!hasAlpha || premult) ?
                                newSample + oldSample * (1.0f - newAlpha) :
                                (newSample * newAlpha + oldSample * oldAlpha * (1.0f - newAlpha)) / alpha;
                        }
                    }
                    break;
                case FrameFlags.BLEND_MULADD:
                    oaf = hasAlpha ? refBuffer[imageColors + info.alphaChannel].getFloatBuffer() : null;
                    naf = hasAlpha ? frameBuffers[frameColors + info.alphaChannel].getFloatBuffer() : null;
                    for (int y = 0; y < frameHeight; y++) {
                        int cy = y + frameStartY;
                        int fy = y + frameOffsetY;
                        for (int x = 0; x < frameWidth; x++) {
                            int cx = x + frameStartX;
                            int fx = x + frameOffsetX;
                            float oldAlpha = hasAlpha ? oaf[cy][cx] : 1.0f;
                            float newAlpha = hasAlpha ? naf[fy][fx] : 1.0f;
                            if (info.clamp)
                                newAlpha = newAlpha < 0.0f ? 0.0f : newAlpha > 1.0f ? 1.0f : newAlpha;
                            float oldSample = rf[cy][cx];
                            float newSample = ff[fy][fx];
                            cf[cy][cx] = isAlpha ? oldAlpha : oldSample + newAlpha * newSample;
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
        Dimension size;
        if (this.imageHeader == null) {
            this.imageHeader = ImageHeader.read(loggers, bitreader, level);
            loggers.log(Loggers.LOG_INFO, "Image: %s", options.input != null ? options.input : "<stdin>");
            loggers.log(Loggers.LOG_INFO, "    Level: %d", level);
            size = imageHeader.getSize();
            loggers.log(Loggers.LOG_INFO, "    Size: %dx%d", size.width, size.height);
            boolean gray = imageHeader.getColorChannelCount() < 3;
            boolean alpha = imageHeader.hasAlpha();
            loggers.log(Loggers.LOG_INFO, "    Pixel Format: %s",
                gray ? (alpha ? "Gray + Alpha" : "Grayscale") : (alpha ? "RGBA" : "RGB"));
            loggers.log(Loggers.LOG_INFO, "    Bit Depth: %d", imageHeader.getBitDepthHeader().bitsPerSample);
            loggers.log(Loggers.LOG_VERBOSE, "    Extra Channels: %d", imageHeader.getExtraChannelCount());
            loggers.log(Loggers.LOG_VERBOSE, "    XYB Encoded: %b", imageHeader.isXYBEncoded());
            ColorEncodingBundle ce = imageHeader.getColorEncoding();
            if (!gray) {
                loggers.log(Loggers.LOG_VERBOSE, "    Primaries: %s",
                    ColorFlags.primariesToString(ce.primaries));
            }
            loggers.log(Loggers.LOG_VERBOSE, "    White Point: %s", ColorFlags.whitePointToString(ce.whitePoint));
            loggers.log(Loggers.LOG_VERBOSE, "    Transfer Function: %s", ColorFlags.transferToString(ce.tf));
            if (imageHeader.getAnimationHeader() != null)
                loggers.log(Loggers.LOG_INFO, "    Animated: true");
            canvas = new ImageBuffer[imageHeader.getColorChannelCount() + imageHeader.getExtraChannelCount()];
        } else {
            size = imageHeader.getSize();
        }

        if (imageHeader.getPreviewSize() != null && !skippedPreview) {
            JXLOptions previewOptions = new JXLOptions(options);
            previewOptions.parseOnly = true;
            Frame frame = new Frame(bitreader, imageHeader, loggers, previewOptions);
            frame.readFrameHeader();
            frame.readTOC();
            frame.skipFrameData();
            skippedPreview = true;
        }

        OpsinInverseMatrix matrix = null;
        if (imageHeader.isXYBEncoded()) {
            ColorEncodingBundle bundle = imageHeader.getColorEncoding();
            matrix = imageHeader.getOpsinInverseMatrix().getMatrix(bundle.prim, bundle.white);
        }

        FrameHeader header;

        do {
            Frame frame = new Frame(bitreader, imageHeader, loggers, options);
            header = frame.readFrameHeader();
            if (totalFrames++ == 0) {
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
            frame.upsample();
            frame.initializeNoise((visibleFrames << 32) | invisibleFrames);
            if (save && header.saveBeforeCT) {
                reference[header.saveAsReference] = Stream.of(frame.getBuffer()).map(
                    b -> new ImageBuffer(b)).toArray(ImageBuffer[]::new);
            }
            computePatches(frame);
            frame.renderSplines();
            frame.synthesizeNoise();
            performColorTransforms(matrix, frame);
            if (header.encoding == FrameFlags.VARDCT && options.renderVarblocks)
                frame.drawVarblocks();
            if (canvas[0] == null) {
                for (int c = 0; c < canvas.length; c++)
                    canvas[c] = new ImageBuffer(frame.getBuffer()[0].getType(), size.height, size.width);
            }
            if (header.type == FrameFlags.REGULAR_FRAME || header.type == FrameFlags.SKIP_PROGRESSIVE) {
                boolean found = false;
                for (int i = 0; i < 4; i++) {
                    if (reference[i] == canvas && i != header.saveAsReference) {
                        found = true;
                        break;
                    }
                }
                if (found)
                    canvas = Stream.of(canvas).map(b -> new ImageBuffer(b)).toArray(ImageBuffer[]::new);
                blendFrame(canvas, frame);
            }
            if (save && !header.saveBeforeCT)
                reference[header.saveAsReference] = canvas;
        } while (!header.isLast && header.duration == 0);

        bitreader.zeroPadToByte();
        byte[] drain = bitreader.drainCache();
        if (drain != null)
            demuxer.pushBack(drain);
        while ((drain = in.drain()) != null)
            demuxer.pushBack(drain);

        if (options.parseOnly)
            return null;

        int orientation = imageHeader.getOrientation();

        ImageBuffer[] orientedCanvas = new ImageBuffer[canvas.length];
        for (int i = 0; i < orientedCanvas.length; i++)
            orientedCanvas[i] = transposeBuffer(canvas[i], orientation);

        return new JXLImage(orientedCanvas, imageHeader);
    }
}
