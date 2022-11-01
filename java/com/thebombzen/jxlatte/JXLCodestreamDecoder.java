package com.thebombzen.jxlatte;

import java.io.IOException;
import java.util.function.DoubleUnaryOperator;

import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.bundle.color.OpsinInverseMatrix;
import com.thebombzen.jxlatte.bundle.color.TransferFunction;
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

    private void invertXYB(double[][][] inputBuffer) {
        // x, y, b
        OpsinInverseMatrix matrix = imageHeader.getOpsinInverseMatrix();
        DoubleUnaryOperator sRGB = TransferFunction.getTransferFunction(TransferFunction.SRGB);
        double cBiasL = MathHelper.signedPow(matrix.opsinBias[0], 1D / 3D);
        double cBiasM = MathHelper.signedPow(matrix.opsinBias[1], 1D / 3D);
        double cBiasS = MathHelper.signedPow(matrix.opsinBias[2], 1D / 3D);

        for (int y = 0; y < imageHeader.getSize().height; y++) {
            for (int x = 0; x < imageHeader.getSize().width; x++) {
                double xybX = inputBuffer[0][y][x];
                double xybY = inputBuffer[1][y][x];
                double xybB = inputBuffer[2][y][x];
                double gammaL = xybY + xybX;
                double gammaM = xybY - xybX;
                double gammaS = xybB;
                double itScale = 255D / imageHeader.getToneMapping().intensityTarget;
                double mixL = (MathHelper.signedPow(gammaL - cBiasL, 3D) + matrix.opsinBias[0]) * itScale;
                double mixM = (MathHelper.signedPow(gammaM - cBiasM, 3D) + matrix.opsinBias[1]) * itScale;
                double mixS = (MathHelper.signedPow(gammaS - cBiasS, 3D) + matrix.opsinBias[2]) * itScale;
                for (int c = 0; c < 3; c++) {
                    double linear709 =
                        matrix.matrix[c][0] * mixL + matrix.matrix[c][1] * mixM + matrix.matrix[c][2] * mixS;
                    inputBuffer[c][y][x] = sRGB.applyAsDouble(linear709);
                }
            }
        }
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
            for (int c = 0; c < buffer.length; c++) {
                for (int y = y0; y < height; y++) {
                    for (int x = x0; x < width; x++) {
                        buffer[c][y][x] = frameBuffer[c][y][x];
                    }
                }
            }
        } while (!frame.getFrameHeader().isLast);
        if (imageHeader.isXYBEncoded()) {
            invertXYB(buffer);
        }
        JXLImage image = new JXLImage(buffer, imageHeader);
        return image;
    }
}
