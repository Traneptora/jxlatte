# jxlatte
Work-in-progress pure java JPEG XL decoder

## Compiling
JXLatte is built with the [Meson build system](https://mesonbuild.com/).

To build, create a build directory, for example, with `mkdir build`.

Then run `meson ../` to set up the build directory, and `ninja` to compile JXLatte.

## Running
JXLatte can be executed just like any normal jar file:

```sh
java -jar jxlatte.jar
```

The JAR can also be used as a library. To use it, add it to your application's classpath.

```java
InputStream input = someInputStream;
JXLatte jxlatte = new JXLLatte(input);
JXLImage image = jxlatte.decode();
```

although at the moment it's too WIP to produce a working image.
