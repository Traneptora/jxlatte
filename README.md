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
JXLatte jxlatte = new JXLatte(input);
JXLImage image = jxlatte.decode();
PNGWriter writer = new PNGWriter(image);
writer.write(output);
```

although at the moment it's too WIP to produce an image for many input files.

## Features
Features currently supported at this time:

- Image Header
  - Image Header parsing
- Entropy decoding: DONE
  - Prefix Coding
  - ANS Coding
- Frame Decoding
  - Frame Header parsing
  - TOC
    - Permutation
    - Parsing
  - LF Global
  - Groups
- Modular mode: DONE
  - Prediction
  - Transforms
    - RCT
    - Palette
    - Squeeze
- VarDCT
  - HF Block Contexts
  - LF Channel Correlation
- Image features
  - Upsampling
  - Patches
  - Splines
- Color transforms
  - XYB
- Rendering: DONE
  - Frame Blending
  - Color Management
  - PNG Output

Features not yet supported at this time:

- Image Header
  - ICC Profile decoding
- VarDCT
  - LFGlobal VarDCT data
    - HF Coefficients
    - LF Coefficients
  - LF Group VarDCT data
  - HF Pass Data
  - Inverse DCT
- Restoration Filters
  - Gaborish
  - Edge Preserving Filter
- Image features
  - Noise Synthesis
- Color transform
  - JPEG Upsampling
  - YCbCr
