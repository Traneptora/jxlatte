package com.thebombzen.jxlatte.bundle;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.bundle.color.ColorEncodingBundle;
import com.thebombzen.jxlatte.bundle.color.ColorFlags;
import com.thebombzen.jxlatte.bundle.color.OpsinInverseMatrix;
import com.thebombzen.jxlatte.bundle.color.ToneMapping;
import com.thebombzen.jxlatte.io.Bitreader;

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
    
    private SizeHeader size;
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
    private int[] alphaIndices;

    private ImageHeader() {

    }

    public static ImageHeader parse(Bitreader reader, int level) throws IOException {
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

    public void setLevel(int level) throws InvalidBitstreamException {
        if (level != 5 && level != 10)
            throw new InvalidBitstreamException();
        this.level = level;
    }
}
