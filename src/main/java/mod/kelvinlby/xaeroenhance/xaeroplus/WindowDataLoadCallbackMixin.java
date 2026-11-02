package mod.kelvinlby.xaeroenhance.xaeroplus;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import mod.kelvinlby.xaeroenhance.FrameWork;
import mod.kelvinlby.xaeroenhance.HighlightMeshes;
import mod.kelvinlby.xaeroenhance.MapScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaeroplus.feature.highlights.ChunkHighlightCacheDimensionHandler;

/**
 * Highlights loaded from the database for a grown map window were all inserted in one frame, which stalls zooming
 * out. They are inserted over a few frames instead, still on the render thread and in order with other changes,
 * after growing the map once up front like {@code putAll} does. Only while the map screen is open; closing it
 * finishes the queued insertions.
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
		FrameWork.submit(deadline -> {
			if (!sized[0]) {
				sized[0] = true;
				chunks.ensureCapacity(chunks.size() + dataBuf.size());
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
				if (key != 0) {
					chunks.put(key, values[slot]);
				}
			}
			if (data.containsNullKey()) {
				chunks.put(0, values[mask + 1]);
			}
			return true;
		});
	}
}
