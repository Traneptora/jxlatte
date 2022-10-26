package com.thebombzen.jxlatte;

import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import java.awt.Point;
import java.awt.color.ColorSpace;
import java.awt.image.*;

import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.entropy.EntropyDecoder;
import com.thebombzen.jxlatte.frame.Frame;
import com.thebombzen.jxlatte.image.ChannelType;
import com.thebombzen.jxlatte.image.JxlImage;
import com.thebombzen.jxlatte.image.JxlImageFormat;
import com.thebombzen.jxlatte.io.Bitreader;

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

    public JxlImage decode(int level) throws IOException {
        this.imageHeader = ImageHeader.parse(bitreader, level);
        if (imageHeader.getColorEncoding().useIccProfile) {
            int encodedSize = Math.toIntExact(bitreader.readU64());
            byte[] encodedIcc = new byte[encodedSize];
            EntropyDecoder iccDistribution = new EntropyDecoder(bitreader, 41);
            for (int i = 0; i < encodedSize; i++)
                encodedIcc[i] = (byte)iccDistribution.readSymbol(bitreader, getICCContext(encodedIcc, i));
        }
        bitreader.zeroPadToByte();
        Frame frame;
        do {
            frame = new Frame(bitreader, imageHeader);
            frame.readHeader();
            WritableRaster buffer = frame.decodeFrame();
            bitreader.zeroPadToByte();
            ColorModel cm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                imageHeader.hasAlpha(), imageHeader.isAlphaPremultiplied(),
                ColorModel.TRANSLUCENT, DataBuffer.TYPE_INT);
            BufferedImage image = new BufferedImage(cm, buffer, false, null);
            BufferedImage pngOut = new BufferedImage(buffer.getWidth(), buffer.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            pngOut.createGraphics().drawImage(image, 0, 0, null);
            ImageIO.write(pngOut, "png", new File("output.png"));
        } while (!frame.getFrameHeader().isLast);
        int width = imageHeader.getSize().width;
        int height = imageHeader.getSize().height;
        JxlImage image =  new JxlImage(new JxlImageFormat(8, 0, ChannelType.PACKED_RGB), width, height);
        return image;
    }
}
