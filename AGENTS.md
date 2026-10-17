# AGENTS.md

Pure-Java JPEG XL decoder (WIP). Parent `com.traneptora:jxlatte:0.1.0-SNAPSHOT` (pom). Modules: `jxlatte-lib` (decoder), `jxlatte-cli` (CLI + PNG/PFM). Zero runtime dependencies. MIT.

## Toolchain

- **Java 21** required (`pom.xml` `java-release.version`, Meson `--release=21`, `jitpack.yml` openjdk21).
- Builds: **Maven** multimodule and **Meson** (explicit source list in `src/meson.build`). Prefer Maven for this fork unless touching Meson packaging.
- Maven ≥ 3.3 (enforcer). Compiler uses `-Xlint`.

```sh
mvn clean package   # lib + shaded CLI JAR
mvn clean install   # also install both modules to local repo
```

Meson:

```sh
mkdir build && cd build && meson setup ../ && ninja
```

## Layout / entrypoints

- `jxlatte-lib/src/main/java/com/traneptora/jxlatte/` — decoder (`JXLDecoder` → `JXLImage`), packages `bundle`, `color`, `entropy`, `frame`, bitstream `io`, `util`
- `jxlatte-cli/` — `JXLatte` (main), `io.PNGWriter`, `io.PFMWriter`
- ICC profiles (classpath root, CLI module): `jxlatte-cli/src/main/resources/{bt2020-d65-pq,bt709-d65-srgb}.icc.zz` — loaded via `PNGWriter.class.getResourceAsStream("/….icc.zz")`
- CLI packages an uber-jar via `maven-shade-plugin` (includes `jxlatte-lib`)

## Meson gotcha

`src/meson.build` lists **every** `.java` and resource by path under `jxlatte-lib` / `jxlatte-cli`. New/renamed sources must be added there or Meson omits them. Maven picks up each module’s `src/main/java` automatically.

`util/ChebyschevApproximation.java` is in `jxlatte-lib` but **not** in the Meson list (Maven-only).

## Verify (no automated tests)

No `src/test` / Surefire suite. Smoke-check with samples:

```sh
java -jar jxlatte-cli/target/jxlatte-cli-0.1.0-SNAPSHOT.jar samples/art.jxl /tmp/out.png
# or after Meson: java -jar build/src/jxlatte-cli.jar samples/art.jxl /tmp/out.png
```

`samples/*.jxl` covers modular/VarDCT/HDR/etc. Root `/*.png` and `/*.jxl` are gitignored (local outputs).

## Versioning

Bump together when releasing: root `pom.xml` / module versions / root `meson.build` version, and `JXLatte.JXLATTE_VERSION`.
