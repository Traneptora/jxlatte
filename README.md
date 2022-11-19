# jxlatte
Work-in-progress pure java JPEG XL decoder

## Compiling
JXLatte is built with the [Meson build system](https://mesonbuild.com/).

To build, create a build directory, for example, with `mkdir build && cd build`.

Then run `meson setup ../` to set up the build directory, and `ninja` to compile JXLatte.

## Running
JXLatte can be executed just like any normal jar file:

```sh
java -jar jxlatte.jar samples/art.jxl output.png
```

The JAR can also be used as a library. To use it, add it to your application's classpath.

```java
InputStream input = someInputStream;
OutputStream output = someOutputStream;
JXLDecoder decoder = new JXLDecoder(input);
JXLImage image = decoder.decode();
PNGWriter writer = new PNGWriter(image);
writer.write(output);
```

although at the moment it's too WIP to produce an image for many input files.

## Features

TL;DR Supported features:
- Anything in Modular Mode
  - All Lossless Images
  - All JXL Art
- All JPEG reconstructions

Features currently implemented at this time:

- Image Header
  - Image Header parsing
- Entropy decoding: DONE
  - Prefix Coding
  - ANS Coding
- Frame Header: DONE
  - Frame Header parsing
  - TOC
    - Permutation
    - Parsing
- Frame Data Sections: DONE
  - LF Global
  - LF Groups
  - HFGlobal / HF Pass Data
  - Pass Groups
- Modular mode: DONE
  - Prediction
  - Transforms
    - RCT
    - Palette
    - Squeeze
- VarDCT: Debugging
  - LF Coefficients
  - HF Coefficients
  - Dequantization Weights
  - Recorrelation (Chroma From Luma)
  - Inverse Transforms
    - Inverse DCT
    - Hornuss
    - AFV
    - Small DCTs
- Image features: DONE
  - Non-separable upsampling
  - Patches
  - Splines
  - Noise Synthesis
- Color transforms: DONE
  - XYB
  - YCbCr
  - JPEG Upsampling
- Rendering: DONE
  - Frame Blending
  - Color Management
  - PNG Output
  - PFM Output
- Restoration Filters:
  - Gaborish
  - EPF 1 Iteration

Features not yet supported at this time:

- Image Header
  - ICC Profile decoding
  - Orientation
- Restoration Filters
  - Edge Preserving Filter, 2-3 Iterations
- VarDCT: Finish Debugging
