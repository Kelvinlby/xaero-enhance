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

## Building

Put these jars in `libs/`, then run `./gradlew build`:

- `xaeroworldmap-fabric-1.21.11-1.47.2.jar`
- `xaerolib-fabric-1.21.11-1.8.5.jar` (nested inside the world map jar under `META-INF/jars/`)
- `XaeroPlus-2.37.2+fabric-1.21.11-WM1.47.2-MM26.6.2.jar` (only needed for `runClient`)
