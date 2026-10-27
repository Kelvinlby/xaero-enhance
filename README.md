# Xaero Enhance

Client-side Fabric mod that improves the performance of Xaero's World Map 1.47.2 (Minecraft 1.21.11). It works with XaeroPlus.

## What it changes

- **The pixel buffers for map chunks are built on worker threads.** Xaero's world map builds each 64x64 chunk texture
  (`MapTileChunk.updateBuffers`) on the render thread, one chunk at a time and under a per-frame time budget.
  This mod moves that work to a small thread pool and builds chunks in parallel. The render thread only copies the
  finished pixels into the texture buffer and uploads it. Regions that are being written around the player still
  use Xaero's own immediate path.
- **The per-frame upload budget always applies.** When the map camera stops moving, Xaero normally drops the budget
  and processes every pending texture in a single frame. That causes the stalls on a zoomed-out map. Because the
  buffers are now built off-thread, the normal budget keeps up.

A worker never touches GPU objects or texture buffers. It writes into a private array, and the result is discarded
if the chunk changed in the meantime. xaerolib's block colour caches are not thread-safe. Workers read concurrent
copies of them, and any missing entries are filled on the render thread.

## Installing

Requirements:

- Minecraft 1.21.11 with Fabric Loader 0.20.1 or newer
- Xaero's World Map 1.47.2 (exactly this version, because the mixins target its internals)
- Java 21 or newer
- XaeroPlus is optional

Steps:

1. Download the latest build. Open the [Actions tab](https://github.com/Kelvinlby/xaero-enhance/actions/workflows/build.yml),
   select the most recent successful run and download the `xaero-enhance-<commit>` artifact. You need to be signed in to
   GitHub to download artifacts. Only the 3 most recent builds are kept.
2. Unzip it. It contains `xaero-enhance-<commit>.jar`.
3. Put the jar in your `.minecraft/mods` folder, next to Xaero's World Map.

The mod is client-side only. Servers don't need it.

## Building

You need JDK 21 or newer. The Xaero jars the build depends on are already included in `libs/`.

```sh
git clone https://github.com/Kelvinlby/xaero-enhance.git
cd xaero-enhance
./gradlew build
```

The mod jar is written to `build/libs/xaero-enhance-<version>.jar`. The version comes from `version` in
`gradle.properties`. You can override it with `-Pversion=...`, which is how CI stamps builds with the commit hash:

```sh
./gradlew build -Pversion=$(git rev-parse --short HEAD)
```

To start a development client with Xaero's World Map and XaeroPlus loaded, run `./gradlew runClient`.

Every pushed commit is built by GitHub Actions (`.github/workflows/build.yml`).
