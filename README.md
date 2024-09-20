# jxlatte
Work-in-progress pure java JPEG XL decoder

## Compiling
### [Meson]

JXLatte is built with the [Meson build system](https://mesonbuild.com/).

To build, create a build directory, for example, with `mkdir build && cd build`.

Then run `meson setup ../` to set up the build directory, and `ninja` to compile JXLatte.

### [Maven]

Now you can also build JXLatte using Maven. Just run `mvn clean install`.

The artifact will be installed to the local repository and then you can reference it in your project like this

Maven
```xml
<dependency>
  <groupId>com.traneptora</groupId>
  <artifactId>jxlatte</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```
Gradle
```groovy
implementation 'com.traneptora:jxlatte:0.0.1-SNAPSHOT'
```
Gradle(Kotlin)
```kotlin
implementation( "com.traneptora:jxlatte:0.0.1-SNAPSHOT")
```

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
