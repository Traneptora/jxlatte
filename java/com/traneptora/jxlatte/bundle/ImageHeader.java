package com.traneptora.jxlatte.bundle;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.traneptora.jxlatte.InvalidBitstreamException;
import com.traneptora.jxlatte.color.ColorEncodingBundle;
import com.traneptora.jxlatte.color.ColorFlags;
import com.traneptora.jxlatte.color.OpsinInverseMatrix;
import com.traneptora.jxlatte.color.ToneMapping;
import com.traneptora.jxlatte.entropy.EntropyStream;
import com.traneptora.jxlatte.io.Bitreader;
import com.traneptora.jxlatte.io.Loggers;
import com.traneptora.jxlatte.util.IntPoint;
import com.traneptora.jxlatte.util.MathHelper;

public class ImageHeader {

    public static final int CODESTREAM_HEADER = 0x0AFF;

    public static final float[] DEFAULT_UP2 = {
        -0.01716200f, -0.03452303f, -0.04022174f, -0.02921014f, -0.00624645f,
        0.14111091f, 0.28896755f, 0.00278718f, -0.01610267f, 0.56661550f,
        0.03777607f, -0.01986694f, -0.03144731f, -0.01185068f, -0.00213539f };
    
    public static final float[] DEFAULT_UP4 = {
        -0.02419067f, -0.03491987f, -0.03693351f, -0.03094285f, -0.00529785f,
        -0.01663432f, -0.03556863f, -0.03888905f, -0.03516850f, -0.00989469f,
        0.23651958f, 0.33392945f, -0.01073543f, -0.01313181f, -0.03556694f,
        0.13048175f, 0.40103025f, 0.03951150f, -0.02077584f, 0.46914198f,
        -0.00209270f, -0.01484589f, -0.04064806f, 0.18942530f, 0.56279892f,
        0.06674400f, -0.02335494f, -0.03551682f, -0.00754830f, -0.02267919f,
        -0.02363578f, 0.00315804f, -0.03399098f, -0.01359519f, -0.00091653f,
        -0.00335467f, -0.01163294f, -0.01610294f, -0.00974088f, -0.00191622f,
        -0.01095446f, -0.03198464f, -0.04455121f, -0.02799790f, -0.00645912f,
        0.06390599f, 0.22963888f, 0.00630981f, -0.01897349f, 0.67537268f,
        0.08483369f, -0.02534994f, -0.02205197f, -0.01667999f, -0.00384443f
    };

    public static final float[] DEFAULT_UP8 = {
        -0.02928613f, -0.03706353f, -0.03783812f, -0.03324558f, -0.00447632f,
        -0.02519406f, -0.03752601f, -0.03901508f, -0.03663285f, -0.00646649f,
        -0.02066407f, -0.03838633f, -0.04002101f, -0.03900035f, -0.00901973f,
        -0.01626393f, -0.03954148f, -0.04046620f, -0.03979621f, -0.01224485f,
        0.29895328f, 0.35757708f, -0.02447552f, -0.01081748f, -0.04314594f,
        0.23903219f, 0.41119301f, -0.00573046f, -0.01450239f, -0.04246845f,
        0.17567618f, 0.45220643f, 0.02287757f, -0.01936783f, -0.03583255f,
        0.11572472f, 0.47416733f, 0.06284440f, -0.02685066f, 0.42720050f,
        -0.02248939f, -0.01155273f, -0.04562755f, 0.28689496f, 0.49093869f,
        -0.00007891f, -0.01545926f, -0.04562659f, 0.21238920f, 0.53980934f,
        0.03369474f, -0.02070211f, -0.03866988f, 0.14229550f, 0.56593398f,
        0.08045181f, -0.02888298f, -0.03680918f, -0.00542229f, -0.02920477f,
        -0.02788574f, -0.02118180f, -0.03942402f, -0.00775547f, -0.02433614f,
        -0.03193943f, -0.02030828f, -0.04044014f, -0.01074016f, -0.01930822f,
        -0.03620399f, -0.01974125f, -0.03919545f, -0.01456093f, -0.00045072f,
        -0.00360110f, -0.01020207f, -0.01231907f, -0.00638988f, -0.00071592f,
        -0.00279122f, -0.00957115f, -0.01288327f, -0.00730937f, -0.00107783f,
        -0.00210156f, -0.00890705f, -0.01317668f, -0.00813895f, -0.00153491f,
        -0.02128481f, -0.04173044f, -0.04831487f, -0.03293190f, -0.00525260f,
        -0.01720322f, -0.04052736f, -0.05045706f, -0.03607317f, -0.00738030f,
        -0.01341764f, -0.03965629f, -0.05151616f, -0.03814886f, -0.01005819f,
        0.18968273f, 0.33063684f, -0.01300105f, -0.01372950f, -0.04017465f,
        0.13727832f, 0.36402234f, 0.01027890f, -0.01832107f, -0.03365072f,
        0.08734506f, 0.38194295f, 0.04338228f, -0.02525993f, 0.56408126f,
        0.00458352f, -0.01648227f, -0.04887868f, 0.24585519f, 0.62026135f,
        0.04314807f, -0.02213737f, -0.04158014f, 0.16637289f, 0.65027023f,
        0.09621636f, -0.03101388f, -0.04082742f, -0.00904519f, -0.02790922f,
        -0.02117818f, 0.00798662f, -0.03995711f, -0.01243427f, -0.02231705f,
        -0.02946266f, 0.00992055f, -0.03600283f, -0.01684920f, -0.00111684f,
        -0.00411204f, -0.01297130f, -0.01723725f, -0.01022545f, -0.00165306f,
        -0.00313110f, -0.01218016f, -0.01763266f, -0.01125620f, -0.00231663f,
        -0.01374149f, -0.03797620f, -0.05142937f, -0.03117307f, -0.00581914f,
        -0.01064003f, -0.03608089f, -0.05272168f, -0.03375670f, -0.00795586f,
        0.09628104f, 0.27129991f, -0.00353779f, -0.01734151f, -0.03153981f,
        0.05686230f, 0.28500998f, 0.02230594f, -0.02374955f, 0.68214326f,
        0.05018048f, -0.02320852f, -0.04383616f, 0.18459474f, 0.71517975f,
        0.10805613f, -0.03263677f, -0.03637639f, -0.01394373f, -0.02511203f,
        -0.01728636f, 0.05407331f, -0.02867568f, -0.01893131f, -0.00240854f,
        -0.00446511f, -0.01636187f, -0.02377053f, -0.01522848f, -0.00333334f,
        -0.00819975f, -0.02964169f, -0.04499287f, -0.02745350f, -0.00612408f,
        0.02727416f, 0.19446600f, 0.00159832f, -0.02232473f, 0.74982506f,
        0.11452620f, -0.03348048f, -0.01605681f, -0.02070339f, -0.00458223f
    };

    private static final char[] MNTRGB = "mntrRGB XYZ ".toCharArray();
    private static final char[] ACSP = "acsp".toCharArray();
    private static final String[] iccTags = new String[]{
        "cprt", "wtpt", "bkpt", "rXYZ", "gXYZ", "bXYZ", "kXYZ", "rTRC", "gTRC",
        "bTRC", "kTRC", "chad", "desc", "chrm", "dmnd", "dmdd", "lumi"
    };

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
    
    private SizeHeader size;
    private int orientedWidth;
    private int orientedHeight;
    private int level = 5;
    private int orientation;
    private SizeHeader intrinsicSize = null;
    private PreviewHeader previewHeader = null;
    private AnimationHeader animationHeader = null;
    private BitDepthHeader bitDepth;
    private boolean modular16bitBuffers = true;
    private ExtraChannelInfo[] extraChannelInfo;
    private boolean xybEncoded = true;
    private ColorEncodingBundle colorEncoding;
    private ToneMapping toneMapping;
    private Extensions extensions;
    private OpsinInverseMatrix opsinInverseMatrix;
    private float[] up2weights;
    private float[] up4weights;
    private float[] up8weights;
    private float[][][][][] upWeights = null;
    private int[] alphaIndices;
    private byte[] encodedICC;
    private byte[] decodedICC = null;

    private ImageHeader() {

    }

    public static ImageHeader parse(Loggers loggers, Bitreader reader, int level) throws IOException {
        ImageHeader header = new ImageHeader();
        if (reader.readBits(16) != CODESTREAM_HEADER)
            throw new InvalidBitstreamException(String.format("Not a JXL Codestream: 0xFF0A magic mismatch"));
        header.setLevel(level);
        header.size = new SizeHeader(reader, level);

        boolean allDefault = reader.readBool();
        boolean extraFields = allDefault ? false : reader.readBool();

        if (extraFields) {
            header.orientation = 1 + reader.readBits(3);
            // have intrinsic size
            if (reader.readBool())
                header.intrinsicSize = new SizeHeader(reader, level);
            // have preview header
            if (reader.readBool())
                header.previewHeader = new PreviewHeader(reader);
            // have animation header
            if (reader.readBool())
                header.animationHeader = new AnimationHeader(reader);
        } else {
            header.orientation = 1;
        }

        if (header.orientation > 4) {
            header.orientedWidth = header.size.height;
            header.orientedHeight = header.size.width;
        } else {
            header.orientedWidth = header.size.width;
            header.orientedHeight = header.size.height;
        }

        if (allDefault) {
            header.bitDepth = new BitDepthHeader();
            header.modular16bitBuffers = true;
            header.extraChannelInfo = new ExtraChannelInfo[0];
            header.xybEncoded = true;
            header.colorEncoding = new ColorEncodingBundle();
        } else {
            header.bitDepth = new BitDepthHeader(reader);
            header.modular16bitBuffers = reader.readBool();
            int extraChannelCount = reader.readU32(0, 0, 1, 0, 2, 4, 1, 12);
            header.extraChannelInfo = new ExtraChannelInfo[extraChannelCount];
            int[] alphaIndices = new int[extraChannelCount];
            int numAlphaChannels = 0;
            for (int i = 0; i < extraChannelCount; i++) {
                header.extraChannelInfo[i] = new ExtraChannelInfo(reader);
                if (header.extraChannelInfo[i].type == ExtraChannelType.ALPHA) {
                    alphaIndices[numAlphaChannels++] = i;
                }
            }
            header.alphaIndices = new int[numAlphaChannels];
            System.arraycopy(alphaIndices, 0, header.alphaIndices, 0, numAlphaChannels);
            header.xybEncoded = reader.readBool();
            header.colorEncoding = new ColorEncodingBundle(reader);
        }

        header.toneMapping = extraFields ? new ToneMapping(reader) : new ToneMapping();
        header.extensions = allDefault ? new Extensions() : new Extensions(reader);

        boolean defaultMatrix = reader.readBool();

        header.opsinInverseMatrix = !defaultMatrix && header.xybEncoded
            ? new OpsinInverseMatrix(reader) : new OpsinInverseMatrix();
        
        int cwMask = defaultMatrix ? 0 : reader.readBits(3);

        if ((cwMask & 1) != 0) {
            header.up2weights = new float[15];
            for (int i = 0; i < header.up2weights.length; i++) {
                header.up2weights[i] = reader.readF16();
            }
        } else {
            header.up2weights = DEFAULT_UP2;
        }

        if ((cwMask & 2) != 0) {
            header.up4weights = new float[55];
            for (int i = 0; i < header.up4weights.length; i++) {
                header.up4weights[i] = reader.readF16();
            }
        } else {
            header.up4weights = DEFAULT_UP4;
        }

        if ((cwMask & 4) != 0) {
            header.up8weights = new float[210];
            for (int i = 0; i < header.up8weights.length; i++) {
                header.up8weights[i] = reader.readF16();
            }
        } else {
            header.up8weights = DEFAULT_UP8;
        }

        if (header.colorEncoding.useIccProfile) {
            int encodedSize;
            try {
                encodedSize = Math.toIntExact(reader.readU64());
            } catch (ArithmeticException ex) {
                throw new InvalidBitstreamException(ex);
            }
            header.encodedICC = new byte[encodedSize];
            EntropyStream iccDistribution = new EntropyStream(loggers, reader, 41);
            for (int i = 0; i < encodedSize; i++)
                header.encodedICC[i] = (byte)iccDistribution.readSymbol(reader, getICCContext(header.encodedICC, i));
            if (!iccDistribution.validateFinalState())
                throw new InvalidBitstreamException("ICC Stream");
        }
        reader.zeroPadToByte();

        return header;
    }

    public int getLevel() {
        return level;
    }

    public SizeHeader getSize() {
        return size;
    }

    public PreviewHeader getPreviewHeader() {
        return previewHeader;
    }

    public SizeHeader getIntrinsticSize() {
        return intrinsicSize;
    }

    public AnimationHeader getAnimationHeader() {
        return animationHeader;
    }

    public int getOrientation() {
        return orientation;
    }

    public BitDepthHeader getBitDepthHeader() {
        return bitDepth;
    }

    public boolean modularUses16BitBuffers() {
        return modular16bitBuffers;
    }

    public int getExtraChannelCount() {
        return extraChannelInfo.length;
    }

    public int getNumAlphaChannels() {
        return alphaIndices != null ? alphaIndices.length : 0;
    }

    public boolean hasAlpha() {
        return getNumAlphaChannels() > 0;
    }

    public int getAlphaIndex(int alphaChannel) {
        return alphaIndices[alphaChannel];
    }

    public int getColorChannelCount() {
        return getColorEncoding().colorEncoding == ColorFlags.CE_GRAY ? 1 : 3;
    }

    public int getTotalChannelCount() {
        return getExtraChannelCount() + getColorChannelCount();
    }

    public ExtraChannelInfo getExtraChannelInfo(int index) {
        return extraChannelInfo[index];
    }

    public boolean isXYBEncoded() {
        return xybEncoded;
    }

    public ColorEncodingBundle getColorEncoding() {
        return colorEncoding;
    }

    public ToneMapping getToneMapping() {
        return toneMapping;
    }

    public int getOrientedWidth() {
        return orientedWidth;
    }

    public int getOrientedHeight() {
        return orientedHeight;
    }

    public Extensions getExtensions() {
        return extensions;
    }

    public OpsinInverseMatrix getOpsinInverseMatrix() {
        return opsinInverseMatrix;
    }

    public float[] getUp2Weights() {
        return up2weights;
    }

    public float[] getUp4Weights() {
        return up4weights;
    }

    public float[] getUp8Weights() {
        return up8weights;
    }

    public float[][][][][] getUpWeights() {
        if (upWeights != null)
            return upWeights;
        upWeights = new float[3][][][][];
        for (int l = 0; l < 3; l++) {
            int k = 1 << (l + 1);
            float[] upKWeights = k == 8 ? getUp8Weights() :
                            k == 4 ? getUp4Weights() :
                            k == 2 ? getUp2Weights() : 
                            null;
            if (upKWeights == null)
                throw new Error("Challenge Complete how did we get here");
            upWeights[l] = new float[k][k][5][5];
            for (int ky = 0; ky < k; ky++) {
                for (int kx = 0; kx < k; kx++) {
                    for (int ix = 0; ix < 5; ix++) {
                        for (int iy = 0; iy < 5; iy++) {
                            int j = (ky < k/2) ? (iy + 5 * ky) : ((4 - iy) + 5 * (k - 1 - ky));
                            int i = (kx < k/2) ? (ix + 5 * kx) : ((4 - ix) + 5 * (k - 1 - kx));
                            int x = Math.max(i, j);
                            int y = Math.min(i, j);
                            int index = 5 * k * y / 2 - y * (y - 1) / 2 + x - y;
                            upWeights[l][ky][kx][iy][ix] = upKWeights[index];
                        }
                    }
                }
            }
        }
        return upWeights;
    }

    public void setLevel(int level) throws InvalidBitstreamException {
        if (level != 5 && level != 10)
            throw new InvalidBitstreamException();
        this.level = level;
    }

    private int getICCPrediction(byte[] buffer, int i) {
        if (i <= 3)
            return buffer.length >>> (8 * (3 - i));
        if (i == 8)
            return 4;
        if (i >= 12 && i <= 23)
            return MNTRGB[i - 12];
        if (i >= 36 && i <= 39)
            return ACSP[i - 36];
        if (buffer[40] == 'A') {
            if (i == 41 || i == 42)
                return 'P';
            if (i == 43)
                return 'L';
        } else if (buffer[40] == 'M') {
            if (i == 41)
                return 'S';
            if (i == 42)
                return 'F';
            if (i == 43)
                return 'T';
        } else if (buffer[40] == 'S') {
            if (buffer[41] == 'G') {
                if (i == 42)
                    return 'I';
                if (i == 43)
                    return 32;
            } else if (buffer[41] == 'U') {
                if (i == 42)
                    return 'N';
                if (i == 43)
                    return 'W';
            }
        }
        if (i == 70)
            return 246;
        if (i == 71)
            return 214;
        if (i == 73)
            return 1;
        if (i == 78)
            return 211;
        if (i == 79)
            return 45;
        if (i >= 80 && i < 84)
            return buffer[i - 76];

        return 0;
    }

    private byte[] shuffle(byte[] buffer, int width) {
        int height = MathHelper.ceilDiv(buffer.length, width);
        byte[] result = new byte[buffer.length];
        for (int i = 0; i < buffer.length; i++) {
            int j = IntPoint.coordinates(i, height).transpose().unwrapCoord(width);
            result[j] = buffer[i];
        }

        return result;
    }

    @SuppressWarnings("resource")
    public synchronized byte[] getDecodedICC() throws IOException {
        if (decodedICC != null)
            return decodedICC;
        if (encodedICC == null)
            return null;
        Bitreader commandReader = new Bitreader(new ByteArrayInputStream(encodedICC));
        int outputSize = commandReader.readICCVarint();
        int commandSize = commandReader.readICCVarint();
        // readICCVarint is always a multiple of bytes
        int commandStart = (int)(commandReader.getBitsCount() >> 3);
        int dataStart = (int)(commandStart + commandSize);
        Bitreader dataReader = new Bitreader(new ByteArrayInputStream(
            encodedICC, dataStart, encodedICC.length - dataStart));
        int headerSize = Math.min(128, outputSize);
        decodedICC = new byte[outputSize];
        int resultPos = 0;

        // header section
        for (int i = 0; i < headerSize; i++) {
            int e = dataReader.readBits(8);
            int p = getICCPrediction(decodedICC, i);
            decodedICC[resultPos++] = (byte)((p + e) & 0xFF);
        }
        if (resultPos == outputSize)
            return decodedICC;

        // taglist section
        int tagCount = commandReader.readICCVarint() - 1;
        if (tagCount >= 0) {
            for (int i = 24; i >= 0; i -= 8)
                decodedICC[resultPos++] = (byte)((tagCount >>> i) & 0xFF);
            int prevTagStart = 128 + tagCount * 12;
            int prevTagSize = 0;
            while (!commandReader.atEnd() && (commandReader.getBitsCount() >> 3) < dataStart) {
                int command = commandReader.readBits(8);
                int tagCode = command & 0x3F;
                if (tagCode == 0)
                    break;
                String tag;
                byte[] tcr;
                if (tagCode == 1) {
                    tcr = new byte[4];
                    for (int i = 0; i < 4; i++)
                        tcr[i] = (byte)dataReader.readBits(8);
                    tag = new String(tcr, StandardCharsets.US_ASCII);
                } else if (tagCode == 2) {
                    tag = "rTRC";
                } else if (tagCode == 3) {
                    tag = "rXYZ";
                } else if (tagCode >= 4 && tagCode <= 21) {
                    tag = iccTags[tagCode - 4];
                } else {
                    throw new InvalidBitstreamException("Illegal ICC Tag Code");
                }

                int tagStart = (command & 0x40) > 0 ? commandReader.readICCVarint() : prevTagStart + prevTagSize;
                int tagSize = (command & 0x80) > 0 ? commandReader.readICCVarint() :
                    Arrays.asList("rXYZ", "gXYZ", "bXYZ", "kXYZ", "wtpt", "bkpt", "lumi")
                    .contains(tag) ? 20 : prevTagSize;

                prevTagSize = tagSize;
                prevTagStart = tagStart;

                String[] tags = tagCode == 2 ? new String[]{"rTRC", "gTRC", "bTRC"}
                    : tagCode == 3 ? new String[]{"rXYZ", "gXYZ", "bXYZ"} : new String[]{tag};
                for (String wTag : tags) {
                    tcr = wTag.getBytes(StandardCharsets.US_ASCII);
                    for (int i = 0; i < 4; i++)
                        decodedICC[resultPos++] = (byte)(tcr[i] & 0xFF);
                    for (int i = 24; i >= 0; i -= 8)
                        decodedICC[resultPos++] = (byte)((tagStart >>> i) & 0xFF);
                    for (int i = 24; i >= 0; i -= 8)
                        decodedICC[resultPos++] = (byte)((tagSize >>> i) & 0xFF);
                    if (tagCode == 3)
                        tagStart += tagSize;
                }
            }
        }

        // data section
        while (!commandReader.atEnd() && (commandReader.getBitsCount() >> 3) < dataStart) {
            int command = commandReader.readBits(8);
            if (command == 1) {
                int num = commandReader.readICCVarint();
                for (int i = 0; i < num; i++)
                    decodedICC[resultPos++] = (byte)dataReader.readBits(8);
            } else if (command == 2 || command == 3) {
                int num = commandReader.readICCVarint();
                byte[] b = new byte[num];
                for (int p = 0; p < num; p++)
                    b[p] = (byte)dataReader.readBits(8);
                int width = command == 2 ? 2 : 4;
                b = shuffle(b, width);
                System.arraycopy(b, 0, decodedICC, resultPos, b.length);
                resultPos += b.length;
            } else if (command == 4) {
                int flags = commandReader.readBits(8);
                int width = (flags & 3) + 1;
                if (width == 3)
                    throw new InvalidBitstreamException("Illegal width=3");
                int order = (flags & 12) >>> 2;
                if (order == 3)
                    throw new InvalidBitstreamException("Illegal order=3");
                int stride = (flags & 0x10) > 0 ? commandReader.readICCVarint() : width;
                if (stride * 4 >= resultPos)
                    throw new InvalidBitstreamException("Stride too large");
                if (stride < width)
                    throw new InvalidBitstreamException("Stride too small");
                int num = commandReader.readICCVarint();
                byte[] b = new byte[num];
                for (int p = 0; p < num; p++)
                    b[p] = (byte)dataReader.readBits(8);
                if (width == 2 || width == 4)
                    b = shuffle(b, width);
                for (int i = 0; i < num; i += width) {
                    int n = order + 1;
                    int[] prev = new int[n];
                    for (int j = 0; j < n; j++) {
                        for (int k = 0; k < width; k++) {
                            prev[j] <<= 8;
                            prev[j] |= decodedICC[resultPos - stride * (j + 1) + k] & 0xFF;
                        }
                    }
                    int p = order == 0 ? prev[0] : order == 1 ? 2 * prev[0] - prev[1]
                        : 3 * prev[0] - 3 * prev[1] + prev[2];
                    for (int j = 0; j < width && i + j < num; j++)
                        decodedICC[resultPos++] = (byte)((b[i + j] + (p >>> (8 * (width - 1 - j)))) & 0xFF);
                }
            } else if (command == 10) {
                decodedICC[resultPos++] = (byte)'X';
                decodedICC[resultPos++] = (byte)'Y';
                decodedICC[resultPos++] = (byte)'Z';
                decodedICC[resultPos++] = (byte)' ';
                resultPos += 4;
                for (int i = 0; i < 12; i++)
                    decodedICC[resultPos++] = (byte)dataReader.readBits(8);
            } else if (command >= 16 && command < 24) {
                String[] s = {"XYZ ", "desc", "text", "mluc", "para", "curv", "sf32", "gbd "};
                char[] trc = s[command - 16].toCharArray();
                for (int i = 0; i < 4; i++)
                    decodedICC[resultPos++] = (byte)trc[i];
                resultPos += 4;
            } else {
                throw new InvalidBitstreamException("Illegal Data Command");
            }
        }

        return decodedICC;
    }
}
