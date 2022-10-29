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
PNGWriter writer = new PNGWriter(image, 8);
writer.write(output);
```

although at the moment it's too WIP to produce an image for most input files.
