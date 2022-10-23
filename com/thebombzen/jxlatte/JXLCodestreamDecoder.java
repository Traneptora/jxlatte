package com.thebombzen.jxlatte;

import java.io.IOException;

import com.thebombzen.jxlatte.bundle.ImageHeader;
import com.thebombzen.jxlatte.image.JxlChannelType;
import com.thebombzen.jxlatte.image.JxlImage;
import com.thebombzen.jxlatte.image.JxlImageFormat;
import com.thebombzen.jxlatte.io.Bitreader;

public class JXLCodestreamDecoder {
    private Bitreader bitreader;
    private ImageHeader imageHeader;

    public JXLCodestreamDecoder(Bitreader in) {
        this.bitreader = in;
    }

    public JxlImage decode() throws IOException {
        this.imageHeader = ImageHeader.parse(bitreader);
        int width = imageHeader.getSize().width;
        int height = imageHeader.getSize().height;
        JxlImage image =  new JxlImage(new JxlImageFormat(8, 0, JxlChannelType.PACKED_RGB), width, height);
        return image;
    }
}
