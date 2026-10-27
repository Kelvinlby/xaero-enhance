package mod.kelvinlby.xaeroenhance.xaeroplus;

import java.util.concurrent.CompletableFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import mod.kelvinlby.xaeroenhance.AsyncBuffers;
import mod.kelvinlby.xaeroenhance.FrameWork;
import mod.kelvinlby.xaeroenhance.HighlightMeshes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaeroplus.feature.highlights.ChunkHighlightCacheDimensionHandler;
import xaeroplus.feature.highlights.ChunkHighlightDatabase;
import xaeroplus.util.ChunkUtils;

/**
 * When the map window moves, XaeroPlus scans every cached highlight on the render thread and removes the ones now
 * outside it, all in one frame. The scan runs on a worker instead, and the removals, which must stay on the render
 * thread, are spread over frames. Each removal and the database write of unsaved entries use exactly the original
 * logic, and the returned future still gates the next window move.
 */
@Mixin(value = ChunkHighlightCacheDimensionHandler.class, remap = false)
public abstract class ChunkHighlightCacheDimensionHandlerMixin {
	@Shadow
	@Final
	private ChunkHighlightDatabase database;
	@Shadow
	@Final
	private ListeningExecutorService dbExecutor;
	@Shadow
	@Final
	private RegistryKey<World> dimension;

	@Inject(method = "flushChunksOutsideWindow", at = @At("HEAD"), cancellable = true)
	private void xaeroEnhance$scanOffThread(int windowRegionX, int windowRegionZ, int windowRegionSize, CallbackInfoReturnable<ListenableFuture<?>> cir) {
		ChunkHighlightCacheDimensionHandler self = (ChunkHighlightCacheDimensionHandler) (Object) this;
		if (!self.mc.isOnThread()) {
			return; // let XaeroPlus raise its own error
		}
		int minX = ChunkUtils.regionCoordToChunkCoord(windowRegionX - windowRegionSize);
		int maxX = ChunkUtils.regionCoordToChunkCoord(windowRegionX + windowRegionSize);
		int minZ = ChunkUtils.regionCoordToChunkCoord(windowRegionZ - windowRegionSize);
		int maxZ = ChunkUtils.regionCoordToChunkCoord(windowRegionZ + windowRegionSize);
		HighlightMeshes.Snapshot snapshot = HighlightMeshes.Snapshot.of(self.chunks);
		SettableFuture<Object> result = SettableFuture.create();
		CompletableFuture.supplyAsync(() -> HighlightMeshes.keysOutside(snapshot, minX, maxX, minZ, maxZ), AsyncBuffers.executor())
				.thenAcceptAsync(outside -> FrameWork.submit(xaeroEnhance$removal(self, outside, minX, maxX, minZ, maxZ, result)), self.mc)
				.exceptionally(t -> {
					result.setException(t);
					return null;
				});
		cir.setReturnValue(result);
	}

	/** Render thread: the body of XaeroPlus's loop, applied to the scanned candidates a few at a time. */
	@Unique
	private FrameWork.Step xaeroEnhance$removal(ChunkHighlightCacheDimensionHandler self, LongArrayList outside, int minX, int maxX, int minZ,
			int maxZ, SettableFuture<Object> result) {
		Long2LongMap dataBuf = new Long2LongOpenHashMap(64);
		int[] next = {0};
		return deadline -> {
			for (; next[0] < outside.size(); next[0]++) {
				if (FrameWork.outOfTime(next[0], deadline)) {
					return false;
				}
				long chunkPos = outside.getLong(next[0]);
				int chunkX = ChunkUtils.longToChunkX(chunkPos);
				int chunkZ = ChunkUtils.longToChunkZ(chunkPos);
				if (!self.chunks.containsKey(chunkPos) || chunkX >= minX && chunkX <= maxX && chunkZ >= minZ && chunkZ <= maxZ) {
					continue;
				}
				if (self.staleChunks.contains(chunkPos)) {
					dataBuf.put(chunkPos, self.chunks.get(chunkPos));
				}
				self.chunks.remove(chunkPos);
			}
			if (dataBuf.isEmpty()) {
				result.set(null);
			} else {
				self.chunks.trim(self.defaultCapacity);
				result.setFuture(dbExecutor.submit(() -> database.insertHighlightList(dataBuf, dimension)));
			}
			return true;
		};
	}
}
