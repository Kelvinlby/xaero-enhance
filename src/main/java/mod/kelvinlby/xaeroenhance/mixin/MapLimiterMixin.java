package mod.kelvinlby.xaeroenhance.mixin;

import java.util.ArrayList;

import mod.kelvinlby.xaeroenhance.MapScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.MapLimiter;
import xaero.map.MapProcessor;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

/**
 * Xaero's map thread calls {@code applyLimit} before every region it loads, and that always takes the render thread
 * pause lock, which the world map screen holds for most of each frame while it extracts its render state. So the map
 * thread waits up to a frame before every region, and loading runs at about one region per frame.
 * <p>
 * {@code applyLimit} only unloads regions once more are loaded than {@code getMaxLoadedRegionsConfig()}. Up to that
 * point its limit is at least the loaded count, so it takes the lock, unloads nothing and lets go. This skips that
 * case, which is every call while the map fills in, and leaves the unloading path untouched. Only while the map
 * screen is open.
 */
@Mixin(MapLimiter.class)
public abstract class MapLimiterMixin {
	/** Only touched by the map thread, like the limiter's own working list. */
	@Unique
	private final ArrayList<MapDimension> xaeroEnhance$dimensions = new ArrayList<>();

	@Inject(method = "applyLimit", at = @At("HEAD"), cancellable = true)
	private void xaeroEnhance$skipWhenUnderLimit(MapWorld mapWorld, MapProcessor mapProcessor, CallbackInfo ci) {
		if (!MapScreen.isOpen()) {
			return;
		}
		xaeroEnhance$dimensions.clear();
		mapWorld.getDimensions(xaeroEnhance$dimensions);
		int loaded = 0;
		for (MapDimension dimension : xaeroEnhance$dimensions) {
			loaded += dimension.getLayeredMapRegions().loadedCount();
		}
		xaeroEnhance$dimensions.clear();
		if (loaded <= mapProcessor.getMaxLoadedRegionsConfig()) {
			ci.cancel();
		}
	}
}
