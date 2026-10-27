package mod.kelvinlby.xaeroenhance.mixin;

import mod.kelvinlby.xaeroenhance.AsyncBuffers;
import mod.kelvinlby.xaeroenhance.AsyncTileChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.region.MapTileChunk;

@Mixin(value = MapTileChunk.class, remap = false)
public abstract class MapTileChunkMixin implements AsyncTileChunk {
	@Unique
	private volatile int xaeroEnhance$generation;
	@Unique
	private volatile AsyncBuffers.Job xaeroEnhance$job;
	@Unique
	private boolean xaeroEnhance$syncFallback;

	@Inject(method = {"setToUpdateBuffers", "setTile", "clean"}, at = @At("HEAD"))
	private void xaeroEnhance$invalidate(CallbackInfo ci) {
		xaeroEnhance$generation++;
	}

	@Override
	public int xaeroEnhance$generation() {
		return xaeroEnhance$generation;
	}

	@Override
	public AsyncBuffers.Job xaeroEnhance$job() {
		return xaeroEnhance$job;
	}

	@Override
	public void xaeroEnhance$setJob(AsyncBuffers.Job job) {
		xaeroEnhance$job = job;
	}

	@Override
	public boolean xaeroEnhance$syncFallback() {
		return xaeroEnhance$syncFallback;
	}

	@Override
	public void xaeroEnhance$setSyncFallback(boolean syncFallback) {
		xaeroEnhance$syncFallback = syncFallback;
	}
}
