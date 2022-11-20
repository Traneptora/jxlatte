package com.thebombzen.jxlatte;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.thebombzen.jxlatte.bundle.ExtraChannelType;
import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.color.CIEPrimaries;
import com.thebombzen.jxlatte.color.CIEXY;
import com.thebombzen.jxlatte.color.ColorEncodingBundle;
import com.thebombzen.jxlatte.color.ColorFlags;
import com.thebombzen.jxlatte.color.OpsinInverseMatrix;
import com.thebombzen.jxlatte.frame.BlendingInfo;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.frame.FrameFlags;
import com.thebombzen.jxlatte.frame.FrameHeader;
import com.thebombzen.jxlatte.frame.features.Patch;
import com.thebombzen.jxlatte.io.Bitreader;
import com.thebombzen.jxlatte.util.FlowHelper;
import com.thebombzen.jxlatte.util.IntPoint;
import com.thebombzen.jxlatte.util.MathHelper;

public class JXLCodestreamDecoder {

    private static double[][] transposeBuffer(double[][] src, int orientation) {
        IntPoint size = IntPoint.sizeOf(src);
        double[][] dest = orientation > 4 ? new double[size.x][size.y]
            : orientation > 1 ? new double[size.y][size.x] : null;
        switch (orientation) {
            case 1:
                return src;
            case 2:
                // flip horizontally
                FlowHelper.parallelIterate(size, p -> {
                    dest[p.y][size.x - 1 - p.x] = src[p.y][p.x];
                });
                return dest;
            case 3:
                // rotate 180 degrees
                FlowHelper.parallelIterate(size, p -> {
                    dest[size.y - 1 - p.y][size.x - 1 - p.x] = src[p.y][p.x];
                });
                return dest;
            case 4:
                // flip vertically
                FlowHelper.parallelIterate(size, p -> {
                    dest[size.y - 1 - p.y][p.x] = src[p.y][p.x];
                });
                return dest;
            case 5:
                // transpose
                FlowHelper.parallelIterate(size, p -> {
                    dest[p.x][p.y] = src[p.y][p.x];
                });
                return dest;
            case 6:
                // rotate clockwise
                FlowHelper.parallelIterate(size, p -> {
                    dest[p.x][size.y - 1 - p.y] = src[p.y][p.x];
                });
                return dest;
            case 7:
                // skew transpose
                FlowHelper.parallelIterate(size, p -> {
                    dest[size.x - 1 - p.x][size.y - 1 - p.y] = src[p.y][p.x];
                });
                return dest;
            case 8:
                // rotate counterclockwise
                FlowHelper.parallelIterate(size, p -> {
                    dest[size.x - 1 - p.x][p.y] = src[p.y][p.x];
                });
                return dest;
            default:
                throw new IllegalStateException("Challenge complete how did we get here");
        }
    }

    private Bitreader bitreader;
    private ImageHeader imageHeader;
    private long flags;

    public JXLCodestreamDecoder(Bitreader in, long flags) {
        this.bitreader = in;
        this.flags = flags;
    }

    private void computePatches(double[][][][] references, Frame frame) throws InvalidBitstreamException {
        FrameHeader header = frame.getFrameHeader();
        double[][][] frameBuffer = frame.getBuffer();
        int colorChannels = imageHeader.getColorChannelCount();
        int extraChannels = imageHeader.getExtraChannelCount();
        Patch[] patches = frame.getLFGlobal().patches;
        for (int i = 0; i < patches.length; i++) {
            Patch patch = patches[i];
            if (patch.ref > 3)
                throw new InvalidBitstreamException("Patch out of range");
            double[][][] refBuffer = references[patch.ref];
            // technically you can reference a nonexistent frame
            // you wouldn't but it's not against the rules
            if (refBuffer == null)
                continue;
            for (int j = 0; j < patch.positions.length; j++) {
                int x0 = patch.positions[j].x;
                int y0 = patch.positions[j].y;
                if (x0 < 0 || y0 < 0)
                    throw new InvalidBitstreamException("Patch size out of bounds");
                if (patch.height + patch.origin.y > refBuffer[1].length
                    || patch.width + patch.origin.x > refBuffer[1][0].length)
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
        double[][][] frameBuffer = frame.getBuffer();
        if (matrix != null) {
            matrix.invertXYB(frameBuffer, imageHeader.getToneMapping().intensityTarget);
        }

        if (frame.getFrameHeader().doYCbCr) {
            FlowHelper.parallelIterate(frame.getPaddedFrameSize(), (x, y) -> {
                double yh = frameBuffer[1][y][x] + 0.5D;
                double cb = frameBuffer[0][y][x];
                double cr = frameBuffer[2][y][x];
                frameBuffer[0][y][x] = yh + 1.402D * cr;
                frameBuffer[1][y][x] = yh - 0.344136D * cb - 0.714136D * cr;
                frameBuffer[2][y][x] = yh + 1.772 * cb;
            });
        }
    }

    private double getSample(double[][] arr, int x, int y) {
        if (y < 0 || y >= arr.length)
            return 0;
        if (x < 0 || x >= arr[y].length)
            return 0;
        return arr[y][x];
    }

    public void blendFrame(double[][][] canvas, double[][][][] reference, Frame frame) throws InvalidBitstreamException {
        int width = imageHeader.getSize().width;
        int height = imageHeader.getSize().height;
        FrameHeader header = frame.getFrameHeader();
        int frameXStart = Math.max(0, header.origin.x);
        int frameYStart = Math.max(0, header.origin.y);
        int frameXEnd = Math.min(width, header.width + header.origin.x);
        int frameYEnd = Math.min(height, header.height + header.origin.y);
        int colorChannels = 3;
        for (int c = 0; c < canvas.length; c++) {
            double[][] newBuffer = frame.getBuffer()[c];
            BlendingInfo info;
            if (c < colorChannels)
                info = frame.getFrameHeader().blendingInfo;
            else
                info = frame.getFrameHeader().ecBlendingInfo[c - colorChannels];
            boolean isAlpha = c >= colorChannels
                && imageHeader.getExtraChannelInfo(c - colorChannels).type == ExtraChannelType.ALPHA;
            boolean premult = imageHeader.hasAlpha()
                        ? imageHeader.getExtraChannelInfo(info.alphaChannel).alphaAssociated
                        : true;
            double[][][] ref = reference[info.source];
            switch (info.mode) {
                case FrameFlags.BLEND_ADD:
                    if (ref != null) {
                        for (int y = frameYStart; y < frameYEnd; y++) {
                            for (int x = frameXStart; x < frameXEnd; x++) {
                                canvas[c][y][x] = frame.getSample(c, x, y) + getSample(ref[c], x, y);
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
                                canvas[c][y][x] = frame.getSample(c, x, y) * getSample(ref[c], x, y);
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
                                getSample(ref[colorChannels + info.alphaChannel], x, y) : 0.0D;
                            double newAlpha = !imageHeader.hasAlpha() ? 1.0D
                                : frame.getSample(colorChannels + info.alphaChannel, x, y);
                            double alpha = 1.0D;
                            double oldSample = ref != null ? getSample(ref[c], x, y) : 0.0D;
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
                                getSample(ref[colorChannels + info.alphaChannel], x, y) : 0.0D;
                            double newAlpha = !imageHeader.hasAlpha() ? 1.0D
                                : frame.getSample(colorChannels + info.alphaChannel, x, y);
                            double alpha = oldAlpha + newAlpha * (1.0D - oldAlpha);
                            if (info.clamp)
                                alpha = MathHelper.clamp(alpha, 0.0D, 1.0D);
                            double oldSample = ref != null ? getSample(ref[c], x, y) : 0.0D;
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
        return decode(level, new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8)));
    }

    public JXLImage decode(int level, PrintWriter err) throws IOException {
        long info = (flags & 0b110) >> 1;
        this.imageHeader = ImageHeader.parse(bitreader, level);
        if (info >= 1) {
            err.println("Image:");
            err.format("    Level: %d%n", level);
            err.format("    Size: %dx%d%n", imageHeader.getSize().width, imageHeader.getSize().height);
            boolean gray = imageHeader.getColorChannelCount() < 3;
            boolean alpha = imageHeader.hasAlpha();
            err.format("    Pixel Format: %s%n",
                gray ? (alpha ? "Gray + Alpha" : "Grayscale") : (alpha ? "RGBA" : "RGB"));
            if (info >= 2) {
                err.format("    Extra Channels: %d%n", imageHeader.getExtraChannelCount());
                err.format("    XYB Encoded: %b%n", imageHeader.isXYBEncoded());
                ColorEncodingBundle ce = imageHeader.getColorEncoding();
                if (!gray)
                    err.format("    Primaries: %s%n", ColorFlags.primariesToString(ce.primaries));
                err.format("    White Point: %s%n", ColorFlags.whitePointToString(ce.whitePoint));
                err.format("    Transfer Function: %s%n", ColorFlags.transferToString(ce.tf));
            }
            if (imageHeader.getAnimationHeader() != null)
                err.format("    Animated: true%n");
            err.flush();
        }

        if (imageHeader.getPreviewHeader() != null) {
            Frame frame = new Frame(bitreader, imageHeader);
            frame.readHeader();
            frame.skipFrameData();
        }

        List<Frame> frames = new ArrayList<>();
        double[][][][] reference = new double[4][][][];
        FrameHeader header;

        do {
            Frame frame = new Frame(bitreader, imageHeader);
            frame.readHeader();
            header = frame.getFrameHeader();
            if (info >= 1 && frames.size() == 0)
                err.format("    Lossless: %s%n",
                    header.encoding == FrameFlags.VARDCT || imageHeader.isXYBEncoded() ? "No" : "Possibly");
            if (info >= 2)
                frame.printDebugInfo(info, err);
            err.flush();
            frame.decodeFrame();
            frames.add(frame);
        } while (!header.isLast);

        OpsinInverseMatrix matrix = null;
        if  (imageHeader.isXYBEncoded()) {
            CIEPrimaries prim = imageHeader.getColorEncoding().prim;
            CIEXY white = imageHeader.getColorEncoding().white;
            matrix = imageHeader.getOpsinInverseMatrix().getMatrix(prim, white);
        }

        double[][][] canvas = new double[3 + imageHeader.getExtraChannelCount()]
            [imageHeader.getSize().height][imageHeader.getSize().width];

        long invisibleFrames = 0;
        long visibleFrames = 0;

        for (Frame frame : frames) {
            header = frame.getFrameHeader();
            boolean save = (header.saveAsReference != 0 || header.duration == 0) && !header.isLast && header.type != FrameFlags.LF_FRAME;
            if (frame.isVisible()) {
                visibleFrames++;
                invisibleFrames = 0;
            } else {
                invisibleFrames++;
            }
            frame.initializeNoise((visibleFrames << 32) | invisibleFrames);
            frame.upsample();
            if (save && header.saveBeforeCT)
                reference[header.saveAsReference] = frame.getBuffer();
            computePatches(reference, frame);
            frame.renderSplines();
            frame.synthesizeNoise();
            performColorTransforms(matrix, frame);
            if (header.type == FrameFlags.REGULAR_FRAME || header.type == FrameFlags.SKIP_PROGRESSIVE) {
                blendFrame(canvas, reference, frame);
            }
            if (save && !header.saveBeforeCT)
                reference[header.saveAsReference] = canvas;
        }

        int orientation = imageHeader.getOrientation();

        int cShift =  3 - imageHeader.getColorChannelCount();

        double[][][] orientedCanvas = new double[canvas.length - cShift][][];

        for (int c = 0; c < canvas.length; c++) {
            int i = cShift > 0 && c < 3 ? 1 : c - cShift;
            orientedCanvas[i] = transposeBuffer(canvas[c], orientation);
        }

        return new JXLImage(orientedCanvas, imageHeader);
    }
}
