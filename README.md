# jxlatte
Work-in-progress pure java JPEG XL decoder

Maven multimodule layout:

- **`jxlatte-lib`** — decoder library (`JXLDecoder`, `JXLImage`, …)
- **`jxlatte-cli`** — CLI (`JXLatte`) plus PNG/PFM writers and ICC resources

## Compiling
### [Meson]

JXLatte is built with the [Meson build system](https://mesonbuild.com/).

To build, create a build directory, for example, with `mkdir build && cd build`.

Then run `meson setup ../` to set up the build directory, and `ninja` to compile JXLatte.

### [Maven]

You can also build JXLatte using Maven. Just run `mvn clean install`.

The library artifact will be installed to the local repository, and then you can reference it in your project like this

Maven
```xml
<dependency>
  <groupId>com.traneptora</groupId>
  <artifactId>jxlatte-lib</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```
Gradle
```groovy
implementation 'com.traneptora:jxlatte-lib:0.1.0-SNAPSHOT'
```
Gradle(Kotlin)
```kotlin
implementation("com.traneptora:jxlatte-lib:0.1.0-SNAPSHOT")
```

For PNG/PFM writers (`PNGWriter`, `PFMWriter`), depend on `jxlatte-cli` as well (or instead, if you only need the CLI JAR).

## Running
JXLatte can be executed just like any normal jar file:

```sh
java -jar jxlatte-cli/target/jxlatte-cli-0.1.0-SNAPSHOT.jar samples/art.jxl output.png
```

The library can also be used from application code:

```java
InputStream input = someInputStream;
OutputStream output = someOutputStream;
JXLDecoder decoder = new JXLDecoder(input);
JXLImage image = decoder.decode();
PNGWriter writer = new PNGWriter(image);
writer.write(output);
```

## Features

Supported features:

- All static Modular images
  - All lossless images
  - All JXL Art
  - All Lossy Modular images
- All static VarDCT Images
  - All JPEG reconstructions
  - Other static VarDCT images
  - Varblock Visualization
- Output:
  - PNG
    - SDR
    - HDR
  - PFM

Features not yet supported at this time:

- Progressive Decoding
- Region-of-interest Decoding
- Animation
