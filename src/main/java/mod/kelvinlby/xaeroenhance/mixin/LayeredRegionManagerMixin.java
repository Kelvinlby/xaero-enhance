package mod.kelvinlby.xaeroenhance.mixin;

import mod.kelvinlby.xaeroenhance.MapScreen;
import mod.kelvinlby.xaeroenhance.RegionIndex;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.region.LayeredRegionManager;
import xaero.map.region.LeveledRegion;

/**
 * Serves the world map screen's per-frame region lookups from {@link RegionIndex}. Only on the render thread while
 * the map screen is open; every other caller, including Xaero's map thread, uses Xaero's own lookup.
 */
@Mixin(LayeredRegionManager.class)
public abstract class LayeredRegionManagerMixin {
	@Inject(method = "get(IIII)Lxaero/map/region/LeveledRegion;", at = @At("HEAD"), cancellable = true)
	private void xaeroEnhance$cachedGet(int caveLayer, int x, int z, int level, CallbackInfoReturnable<LeveledRegion<?>> cir) {
		if (level < 0 || level > 3 || !MapScreen.isOpen() || !Minecraft.getInstance().isSameThread()) {
			return;
		}
		cir.setReturnValue(RegionIndex.get((LayeredRegionManager) (Object) this, caveLayer, x, z, level));
	}

	@Inject(method = "clear", at = @At("RETURN"))
	private void xaeroEnhance$cleared(CallbackInfo ci) {
		RegionIndex.invalidate();
	}
}
