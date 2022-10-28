package com.thebombzen.jxlatte.bundle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.thebombzen.jxlatte.InvalidBitstreamException;
import com.thebombzen.jxlatte.image.ExtraChannelType;
import com.thebombzen.jxlatte.io.Bitreader;

public class ExtraChannelInfo {
    public final int type;
    public final BitDepthHeader bitDepth;
    public final int dimShift;
    public final String name;
    public final boolean alphaAssociated;
    public final float red, green, blue, solidity;
    public final int cfaIndex;

    public ExtraChannelInfo(Bitreader reader) throws IOException {
        boolean d_alpha = reader.readBool();
        if (!d_alpha) {
            type = reader.readEnum();
            if (!ExtraChannelType.validate(type))
                throw new InvalidBitstreamException("Illegal extra channel type");
            bitDepth = new BitDepthHeader(reader);
            dimShift = reader.readU32(0, 0, 3, 0, 4, 0, 1, 3);
            int nameLen = reader.readU32(0, 0, 0, 4, 16, 5, 48, 10);
            byte[] nameBuffer = new byte[nameLen];
            // because we have no byte-alignment guarantee we can't be more efficient
            for (int i = 0; i < nameLen; i++)
                nameBuffer[i] = (byte)reader.readBits(8);
            // by spec, this name buffer is always UTF-8
            name = new String(nameBuffer, StandardCharsets.UTF_8);
            // take advantage of lazy &&
            alphaAssociated = (type == ExtraChannelType.ALPHA) && reader.readBool();
        } else {
            type = ExtraChannelType.ALPHA;
            bitDepth = new BitDepthHeader();
            dimShift = 0;
            name = "";
            alphaAssociated = false;
        }

        if (type == ExtraChannelType.SPOT_COLOR) {
            red = reader.readF16();
            green = reader.readF16();
            blue = reader.readF16();
            solidity = reader.readF16();
        } else {
            red = 0;
            green = 0;
            blue = 0;
            solidity = 0;
        }

        if (type == ExtraChannelType.COLOR_FILTER_ARRAY)
            this.cfaIndex = reader.readU32(1, 0, 0, 2, 3, 4, 19, 8);
        else
            this.cfaIndex = 1;
    }
}
