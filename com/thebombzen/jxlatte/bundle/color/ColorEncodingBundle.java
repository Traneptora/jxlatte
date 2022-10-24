package com.thebombzen.jxlatte.bundle.color;

import java.io.IOException;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.io.Bitreader;

public class ColorEncodingBundle {
    public final boolean useIccProfile;
    public final EnumColorSpace colorSpace;
    public final EnumWhitePoint whitePoint;
    public final CIEXY white;
    public final EnumColorPrimaries primaries;
    public final CIEXY pRed;
    public final CIEXY pGreen;
    public final CIEXY pBlue;
    public final TransferFunction tf;
    public final EnumRenderingIntent renderingIntent;

    public ColorEncodingBundle() {
        useIccProfile = false;
        colorSpace = EnumColorSpace.RGB;
        whitePoint = EnumWhitePoint.D65;
        white = whitePoint.xy;
        primaries = EnumColorPrimaries.SRGB;
        pRed = primaries.red;
        pGreen = primaries.green;
        pBlue = primaries.blue;
        tf = new TransferFunction();
        renderingIntent = EnumRenderingIntent.RELATIVE;
    }

    public ColorEncodingBundle(Bitreader reader) throws IOException {
        boolean allDefault = reader.readBool();
        useIccProfile = allDefault ? false : reader.readBool();
        if (!allDefault)
            colorSpace = EnumColorSpace.getForIndex(reader.readEnum());
        else
            colorSpace = EnumColorSpace.RGB;
        if (colorSpace == null)
            throw new InvalidBitstreamException("Invalid ColorSpace enum");
        if (!allDefault && !useIccProfile && colorSpace != EnumColorSpace.XYB)
            whitePoint = EnumWhitePoint.getForIndex(reader.readEnum());
        else
            whitePoint = EnumWhitePoint.D65;
        if (whitePoint == null)
            throw new InvalidBitstreamException("Invalid WhitePoint enum");
        if (whitePoint == EnumWhitePoint.CUSTOM)
            white = new CustomXY(reader);
        else
            white = whitePoint.xy;
        if (!allDefault && !useIccProfile && colorSpace != EnumColorSpace.XYB && colorSpace != EnumColorSpace.GRAY)
            primaries = EnumColorPrimaries.getForIndex(reader.readEnum());
        else
            primaries = EnumColorPrimaries.SRGB;
        if (primaries == null)
            throw new InvalidBitstreamException("Invalid Primaries enum");
        if (primaries == EnumColorPrimaries.CUSTOM) {
            pRed = new CustomXY(reader);
            pGreen = new CustomXY(reader);
            pBlue = new CustomXY(reader);
        } else {
            pRed = primaries.red;
            pGreen = primaries.green;
            pBlue = primaries.blue;
        }
        if (!allDefault && !useIccProfile) {
            tf = new TransferFunction(reader);
            renderingIntent = EnumRenderingIntent.getForIndex(reader.readEnum());
            if (renderingIntent == null)
                throw new InvalidBitstreamException("Invalid RenderingIntent enum");
        } else {
            tf = new TransferFunction();
            renderingIntent = EnumRenderingIntent.RELATIVE;
        }
    }
}
