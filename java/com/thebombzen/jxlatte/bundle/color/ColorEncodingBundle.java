package com.thebombzen.jxlatte.bundle.color;

import java.io.IOException;
import java.util.Objects;

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
        colorSpace = ColorSpace.RGB;
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
            colorSpace = ColorSpace.RGB;
        if (!ColorSpace.validate(colorSpace))
            throw new InvalidBitstreamException("Invalid ColorSpace enum");
        if (!allDefault && !useIccProfile && colorSpace != ColorSpace.XYB)
            whitePoint = reader.readEnum();
        else
            whitePoint = WhitePoint.D65;
        if (!WhitePoint.validate(whitePoint))
            throw new InvalidBitstreamException("Invalid WhitePoint enum");
        if (whitePoint == WhitePoint.CUSTOM)
            white = new CustomXY(reader);
        else
            white = WhitePoint.getWhitePoint(whitePoint);
        if (!allDefault && !useIccProfile && colorSpace != ColorSpace.XYB && colorSpace != ColorSpace.GRAY)
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

    @Override
    public int hashCode() {
        return Objects.hash(useIccProfile, colorSpace, whitePoint, white, primaries, prim, tf, renderingIntent);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ColorEncodingBundle other = (ColorEncodingBundle) obj;
        return useIccProfile == other.useIccProfile && colorSpace == other.colorSpace && whitePoint == other.whitePoint
                && Objects.equals(white, other.white) && primaries == other.primaries
                && Objects.equals(prim, other.prim) && tf == other.tf && renderingIntent == other.renderingIntent;
    }

    @Override
    public String toString() {
        return "ColorEncodingBundle [useIccProfile=" + useIccProfile + ", colorSpace=" + colorSpace + ", whitePoint="
                + whitePoint + ", white=" + white + ", primaries=" + primaries + ", prim=" + prim + ", tf=" + tf
                + ", renderingIntent=" + renderingIntent + "]";
    }
}
