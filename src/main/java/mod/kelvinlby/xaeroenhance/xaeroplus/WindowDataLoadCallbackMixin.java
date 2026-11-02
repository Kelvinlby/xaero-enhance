package mod.kelvinlby.xaeroenhance.xaeroplus;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import mod.kelvinlby.xaeroenhance.FrameWork;
import mod.kelvinlby.xaeroenhance.HighlightCacheAccess;
import mod.kelvinlby.xaeroenhance.HighlightMeshes;
import mod.kelvinlby.xaeroenhance.MapScreen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaeroplus.Globals;
import xaeroplus.feature.highlights.ChunkHighlightCacheDimensionHandler;
import xaeroplus.util.ChunkUtils;

/**
 * Highlights loaded from the database for a grown map window were all inserted in one frame, which stalls zooming
 * out. They are inserted over a few frames instead, still on the render thread and in order with other changes,
 * after growing the map once up front like {@code putAll} does. Only while the map screen is open. Closing it finishes
 * the queued insertions, but only for highlights near the player: XaeroPlus shrinks the window back to the minimap's
 * on its next tick and would drop the rest again straight away.
 */
@Mixin(targets = "xaeroplus.feature.highlights.ChunkHighlightCacheDimensionHandler$WindowDataLoadFutureCallback")
public abstract class WindowDataLoadCallbackMixin {
	@Shadow
	@Final
	ChunkHighlightCacheDimensionHandler this$0;

	@Unique
	private static final int SCATTER = 0x9E3779B9;

	@Inject(method = "onSuccess(Lit/unimi/dsi/fastutil/longs/Long2LongMap;)V", at = @At("HEAD"), cancellable = true)
	private void xaeroEnhance$insertOverFrames(Long2LongMap dataBuf, CallbackInfo ci) {
		if (dataBuf.isEmpty() || !this$0.mc.isSameThread() || !MapScreen.isOpen()) {
			return; // XaeroPlus's own path, including its wrong-thread report
		}
		ci.cancel();
		Long2LongOpenHashMap chunks = this$0.chunks;
		// Nothing else touches dataBuf, so its backing arrays can be read directly.
		HighlightMeshes.Snapshot data = HighlightMeshes.Snapshot.of(dataBuf);
		long[] keys = data.keys();
		long[] values = data.values();
		int mask = keys.length - 2; // slots 0..n-1 with n a power of two; slot n holds the null key
		int[] next = {0};
		boolean[] sized = {false};
		int[][] keep = {null};
		boolean[] closed = {false};
		FrameWork.submit(deadline -> {
			if (!closed[0] && !MapScreen.isOpen()) {
				closed[0] = true;
				keep[0] = xaeroEnhance$minimapWindow(this$0);
			}
			if (!sized[0]) {
				sized[0] = true;
				if (keep[0] == null) {
					chunks.ensureCapacity(chunks.size() + dataBuf.size());
				}
			}
			// Visit dataBuf's slots in a scrambled order (an odd stride is a permutation of a power-of-two table).
			// In slot order the keys arrive sorted by hash, and once the target table is smaller than dataBuf's,
			// which removals between frames cause by shrinking it, they pile into a single probe run that makes
			// each put scan most of the table.
			for (; next[0] <= mask; next[0]++) {
				if (FrameWork.outOfTime(next[0], deadline)) {
					return false;
				}
				int slot = next[0] * SCATTER & mask;
				long key = keys[slot];
				if (key != 0 && xaeroEnhance$inside(keep[0], key)) {
					chunks.put(key, values[slot]);
				}
			}
			if (data.containsNullKey() && xaeroEnhance$inside(keep[0], 0)) {
				chunks.put(0, values[mask + 1]);
			}
			return true;
		});
	}

	/**
	 * Chunk bounds of the window XaeroPlus moves this cache to once the map screen is closed, with a region of margin
	 * in case the player crosses into the next region first, or null if that window cannot be predicted. This mirrors
	 * {@code ChunkHighlightSavingCache.handleTick}: without the map screen the window is centred on the player's region
	 * and spans {@code getMinimapRegionWindowSize()} regions each way. That only applies to the cache of the
	 * dimension the player is in, and only when the map was showing that dimension.
	 */
	@Unique
	private static int @Nullable [] xaeroEnhance$minimapWindow(ChunkHighlightCacheDimensionHandler handler) {
		try {
			ResourceKey<Level> dimension = ((HighlightCacheAccess) handler).xaeroEnhance$dimension();
			if (!dimension.equals(Globals.getCurrentDimensionId()) || !dimension.equals(ChunkUtils.getActualDimension())) {
				return null;
			}
			int size = Math.max(3, Globals.minimapScaleMultiplier) + 1;
			int regionX = ChunkUtils.getPlayerRegionX();
			int regionZ = ChunkUtils.getPlayerRegionZ();
			return new int[] {
					ChunkUtils.regionCoordToChunkCoord(regionX - size), ChunkUtils.regionCoordToChunkCoord(regionX + size + 1),
					ChunkUtils.regionCoordToChunkCoord(regionZ - size), ChunkUtils.regionCoordToChunkCoord(regionZ + size + 1)};
		} catch (RuntimeException e) {
			return null; // no player or level; keep everything
		}
	}

	@Unique
	private static boolean xaeroEnhance$inside(int @Nullable [] window, long chunkPos) {
		if (window == null) {
			return true;
		}
		int x = ChunkUtils.longToChunkX(chunkPos);
		int z = ChunkUtils.longToChunkZ(chunkPos);
		return x >= window[0] && x < window[1] && z >= window[2] && z < window[3];
	}
}
