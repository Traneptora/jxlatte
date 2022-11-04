package com.thebombzen.jxlatte;

import java.io.IOException;

import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.entropy.EntropyStream;
import com.thebombzen.jxlatte.frame.Frame;
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

    public JXLImage decode(int level) throws IOException {
        this.imageHeader = ImageHeader.parse(bitreader, level);
        if (imageHeader.getColorEncoding().useIccProfile) {
            int encodedSize = Math.toIntExact(bitreader.readU64());
            byte[] encodedIcc = new byte[encodedSize];
            EntropyStream iccDistribution = new EntropyStream(bitreader, 41);
            for (int i = 0; i < encodedSize; i++)
                encodedIcc[i] = (byte)iccDistribution.readSymbol(bitreader, getICCContext(encodedIcc, i));
        }
        bitreader.zeroPadToByte();

        Frame frame;
        if (imageHeader.getPreviewHeader() != null) {
            frame = new Frame(bitreader, imageHeader);
            frame.readHeader();
            frame.skipFrameData();
        }

        int height = imageHeader.getSize().height;
        int width = imageHeader.getSize().width;
        double[][][] buffer = new double[imageHeader.getTotalChannelCount()][height][width];

        do {
            frame = new Frame(bitreader, imageHeader);
            frame.readHeader();
            double[][][] frameBuffer = frame.decodeFrame();
            int x0 = Math.max(frame.getFrameHeader().x0, 0);
            int y0 = Math.max(frame.getFrameHeader().y0, 0);
            int frameWidth = frame.getFrameHeader().width;
            int frameHeight = frame.getFrameHeader().height;
            if (frameWidth + x0 > width)
                frameWidth = width - x0;
            if (frameHeight + x0 > height)
                frameHeight = height - x0;
            for (int c = 0; c < buffer.length; c++) {
                for (int y = y0; y < frameHeight; y++)
                    System.arraycopy(frameBuffer[c][y], x0, buffer[c][y], 0, frameWidth);
            }
        } while (!frame.getFrameHeader().isLast);

        if (imageHeader.isXYBEncoded())
            imageHeader.getOpsinInverseMatrix().invertXYB(buffer, imageHeader.getToneMapping().intensityTarget);

        return new JXLImage(buffer, imageHeader);
    }
}
