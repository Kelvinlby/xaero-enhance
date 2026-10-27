package mod.kelvinlby.xaeroenhance.xaeroplus;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import mod.kelvinlby.xaeroenhance.FrameWork;
import mod.kelvinlby.xaeroenhance.MapScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
@Mixin(targets = "xaeroplus.feature.highlights.ChunkHighlightCacheDimensionHandler$WindowDataLoadFutureCallback", remap = false)
public abstract class WindowDataLoadCallbackMixin {
	@Shadow
	@Final
	ChunkHighlightCacheDimensionHandler this$0;

	@Inject(method = "onSuccess(Lit/unimi/dsi/fastutil/longs/Long2LongMap;)V", at = @At("HEAD"), cancellable = true)
	private void xaeroEnhance$insertOverFrames(Long2LongMap dataBuf, CallbackInfo ci) {
		if (dataBuf.isEmpty() || !this$0.mc.isOnThread() || !MapScreen.isOpen()) {
			return; // XaeroPlus's own path, including its wrong-thread report
		}
		ci.cancel();
		Long2LongOpenHashMap chunks = this$0.chunks;
		ObjectIterator<Long2LongMap.Entry> entries = Long2LongMaps.fastIterator(dataBuf);
		boolean[] sized = {false};
		FrameWork.submit(deadline -> {
			if (!sized[0]) {
				// Grow once up front, as putAll does. Inserting another hash map's entries in its iteration order
				// into a smaller, growing table piles them into ever longer probe runs.
				sized[0] = true;
				chunks.ensureCapacity(chunks.size() + dataBuf.size());
			}
			for (int i = 1; entries.hasNext(); i++) {
				Long2LongMap.Entry entry = entries.next();
				chunks.put(entry.getLongKey(), entry.getLongValue());
				if (FrameWork.outOfTime(i, deadline)) {
					return !entries.hasNext();
				}
			}
			return true;
		});
	}
}
