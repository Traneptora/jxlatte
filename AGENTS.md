# AGENTS.md

Pure-Java JPEG XL decoder (WIP). Artifact `com.traneptora:jxlatte:0.1.0`. Zero runtime dependencies. MIT.

## Toolchain

- **Java 21** required (`pom.xml` `java-release.version`, Meson `--release=21`).
- Builds: **Maven** (standard layout) and **Meson** (explicit source list). Prefer Maven for this fork unless touching Meson packaging.
- Maven ≥ 3.3 (enforcer). Compiler uses `-Xlint`.

```sh
mvn clean package          # JAR in target/; mainClass com.traneptora.jxlatte.JXLatte
mvn clean install          # also install to local repo
```

Meson:

```sh
mkdir build && cd build && meson setup ../ && ninja
```

`jitpack.yml` still pins OpenJDK 11 while the project requires 21 — treat as stale for local/Java work; do not “fix” builds down to 11.

## Layout / entrypoints

- Sources: `src/main/java/com/traneptora/jxlatte/`
  - CLI: `JXLatte`
  - Library: `JXLDecoder` → `JXLImage`; writers `io.PNGWriter`, `io.PFMWriter`
- Packages mirror decode pipeline: `bundle`, `color`, `entropy`, `frame` (modular / vardct / features), `io`, `util`
- ICC profiles (must stay on classpath root): `src/main/resources/{bt2020-d65-pq,bt709-d65-srgb}.icc.zz` — loaded via `JXLatte.class.getResourceAsStream("/….icc.zz")`. `pom.xml` lists them explicitly under `<resources>`.

## Meson gotcha

`src/meson.build` lists **every** `.java` and resource by path. New/renamed sources must be added there or Meson omits them. Maven picks up `src/main/java` automatically.

Currently `util/ChebyschevApproximation.java` is in the tree but **not** in the Meson list (Maven-only).

## Verify (no automated tests)

No `src/test` / Surefire suite. Smoke-check with samples:

```sh
java -jar target/jxlatte-0.1.0.jar samples/art.jxl /tmp/out.png
# or after Meson: java -jar build/src/jxlatte.jar samples/art.jxl /tmp/out.png
```

`samples/*.jxl` covers modular/VarDCT/HDR/etc. Root `/*.png` and `/*.jxl` are gitignored (local outputs).

## Versioning

Bump together when releasing: `pom.xml` / root `meson.build` version, and `JXLatte.JXLATTE_VERSION`.
