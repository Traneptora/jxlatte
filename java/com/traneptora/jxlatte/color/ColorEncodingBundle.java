package com.thebombzen.jxlatte.color;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.io.Bitreader;

public class ColorEncodingBundle {
    public final boolean useIccProfile;
    public final int colorEncoding;
    public final int whitePoint;
    public final CIEXY white;
    public final int primaries;
    public final CIEPrimaries prim;
    public final int tf;
    public final int renderingIntent;

    public ColorEncodingBundle() {
        useIccProfile = false;
        colorEncoding = ColorFlags.CE_RGB;
        whitePoint = ColorFlags.WP_D65;
        white = ColorFlags.getWhitePoint(whitePoint);
        primaries = ColorFlags.PRI_SRGB;
        prim = ColorFlags.getPrimaries(primaries);
        tf = ColorFlags.TF_SRGB;
        renderingIntent = ColorFlags.RI_RELATIVE;
    }

    public ColorEncodingBundle(Bitreader reader) throws IOException {
        boolean allDefault = reader.readBool();
        useIccProfile = allDefault ? false : reader.readBool();
        if (!allDefault)
            colorEncoding = reader.readEnum();
        else
            colorEncoding = ColorFlags.CE_RGB;
        if (!ColorFlags.validateColorEncoding(colorEncoding))
            throw new InvalidBitstreamException("Invalid ColorSpace enum");
        if (!allDefault && !useIccProfile && colorEncoding != ColorFlags.CE_XYB)
            whitePoint = reader.readEnum();
        else
            whitePoint = ColorFlags.WP_D65;
        if (!ColorFlags.validateWhitePoint(whitePoint))
            throw new InvalidBitstreamException("Invalid WhitePoint enum");
        if (whitePoint == ColorFlags.WP_CUSTOM)
            white = new CustomXY(reader);
        else
            white = ColorFlags.getWhitePoint(whitePoint);
        if (!allDefault && !useIccProfile && colorEncoding != ColorFlags.CE_XYB && colorEncoding != ColorFlags.CE_GRAY)
            primaries = reader.readEnum();
        else
            primaries = ColorFlags.PRI_SRGB;
        if (!ColorFlags.validatePrimaries(primaries))
            throw new InvalidBitstreamException("Invalid Primaries enum");
        if (primaries == ColorFlags.PRI_CUSTOM) {
            CIEXY pRed = new CustomXY(reader);
            CIEXY pGreen = new CustomXY(reader);
            CIEXY pBlue = new CustomXY(reader);
            prim = new CIEPrimaries(pRed, pGreen, pBlue);
        } else {
            prim = ColorFlags.getPrimaries(primaries);
        }
        if (!allDefault && !useIccProfile) {
            boolean useGamma = reader.readBool();
            if (useGamma)
                tf = reader.readBits(24);
            else
                tf = (1 << 24) + reader.readEnum();
            if (!ColorFlags.validateTransfer(tf))
                throw new InvalidBitstreamException("Illegal transfer function");
            renderingIntent = reader.readEnum();
            if (!ColorFlags.validateRenderingIntent(renderingIntent))
                throw new InvalidBitstreamException("Invalid RenderingIntent enum");
        } else {
            tf = ColorFlags.TF_SRGB;
            renderingIntent = ColorFlags.RI_RELATIVE;
        }
    }

    @Override
    public String toString() {
        return String.format(
                "ColorEncodingBundle [useIccProfile=%s, colorEncoding=%s, whitePoint=%s, white=%s, primaries=%s, prim=%s, tf=%s, renderingIntent=%s]",
                useIccProfile, colorEncoding, whitePoint, white, primaries, prim, tf, renderingIntent);
    }

}
