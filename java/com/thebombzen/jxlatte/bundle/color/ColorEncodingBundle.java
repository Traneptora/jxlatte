package com.thebombzen.jxlatte.bundle.color;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.io.Bitreader;

public class ColorEncodingBundle {
    public final boolean useIccProfile;
    public final int colorSpace;
    public final int whitePoint;
    public final CIEXY white;
    public final int primaries;
    public final CIEPrimaries prim;
    public final int tf;
    public final int renderingIntent;

    public ColorEncodingBundle() {
        useIccProfile = false;
        colorSpace = ColorEncoding.RGB;
        whitePoint = WhitePoint.D65;
        white = WhitePoint.getWhitePoint(whitePoint);
        primaries = Primaries.SRGB;
        prim = Primaries.getPrimaries(primaries);
        tf = TransferFunction.SRGB;
        renderingIntent = RenderingIntent.RELATIVE;
    }

    public ColorEncodingBundle(Bitreader reader) throws IOException {
        boolean allDefault = reader.readBool();
        useIccProfile = allDefault ? false : reader.readBool();
        if (!allDefault)
            colorSpace = reader.readEnum();
        else
            colorSpace = ColorEncoding.RGB;
        if (!ColorEncoding.validate(colorSpace))
            throw new InvalidBitstreamException("Invalid ColorSpace enum");
        if (!allDefault && !useIccProfile && colorSpace != ColorEncoding.XYB)
            whitePoint = reader.readEnum();
        else
            whitePoint = WhitePoint.D65;
        if (!WhitePoint.validate(whitePoint))
            throw new InvalidBitstreamException("Invalid WhitePoint enum");
        if (whitePoint == WhitePoint.CUSTOM)
            white = new CustomXY(reader);
        else
            white = WhitePoint.getWhitePoint(whitePoint);
        if (!allDefault && !useIccProfile && colorSpace != ColorEncoding.XYB && colorSpace != ColorEncoding.GRAY)
            primaries = reader.readEnum();
        else
            primaries = Primaries.SRGB;
        if (!Primaries.validate(primaries))
            throw new InvalidBitstreamException("Invalid Primaries enum");
        if (primaries == Primaries.CUSTOM) {
            CIEXY pRed = new CustomXY(reader);
            CIEXY pGreen = new CustomXY(reader);
            CIEXY pBlue = new CustomXY(reader);
            prim = new CIEPrimaries(pRed, pGreen, pBlue);
        } else {
            prim = Primaries.getPrimaries(primaries);
        }
        if (!allDefault && !useIccProfile) {
            boolean useGamma = reader.readBool();
            if (useGamma) {
                tf = reader.readBits(24);
            } else {
                tf = (1 << 24) + reader.readEnum();
            }
            if (!TransferFunction.validate(tf))
                throw new InvalidBitstreamException("Illegal transfer function");
            renderingIntent = reader.readEnum();
            if (!RenderingIntent.validate(renderingIntent))
                throw new InvalidBitstreamException("Invalid RenderingIntent enum");
        } else {
            tf = TransferFunction.SRGB;
            renderingIntent = RenderingIntent.RELATIVE;
        }
    }
}
