package mod.kelvinlby.xaeroenhance.mixin;

import mod.kelvinlby.xaeroenhance.AsyncBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.MapProcessor;
import xaero.map.biome.BlockTintProvider;
import xaero.map.cache.BlockStateShortShapeCache;
import xaero.map.region.LeveledRegion;
import xaero.map.region.MapTileChunk;
import xaero.map.region.MapUpdateFastConfig;
import xaero.map.region.OverlayManager;
import xaero.map.region.texture.LeafRegionTexture;

/**
 * Both hooks run on the render thread inside {@code MapProcessor.onRenderProcess}. preUpload is only reached
 * within the frame's upload budget, postUpload for every loaded texture, so jobs get submitted even when the
 * budget is spent.
 */
@Mixin(value = LeafRegionTexture.class, remap = false)
public abstract class LeafRegionTextureMixin {
	@Shadow
	private MapTileChunk tileChunk;

	@Inject(method = "preUpload", at = @At("HEAD"), cancellable = true)
	private void xaeroEnhance$preUpload(MapProcessor mapProcessor, BlockTintProvider blockTintProvider, OverlayManager overlayManager,
			LeveledRegion<LeafRegionTexture> region, boolean detailedDebug, BlockStateShortShapeCache shapeCache,
			MapUpdateFastConfig updateConfig, CallbackInfo ci) {
		if (AsyncBuffers.handle(tileChunk, mapProcessor, true)) {
			ci.cancel();
		}
	}

	@Inject(method = "postUpload", at = @At("HEAD"))
	private void xaeroEnhance$postUpload(MapProcessor mapProcessor, LeveledRegion<LeafRegionTexture> region, boolean cleanAndCacheRequestsBlocked,
			CallbackInfo ci) {
		AsyncBuffers.handle(tileChunk, mapProcessor, false);
	}
}
