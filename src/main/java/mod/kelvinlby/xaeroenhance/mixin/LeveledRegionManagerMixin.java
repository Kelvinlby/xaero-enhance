package mod.kelvinlby.xaeroenhance.mixin;

import mod.kelvinlby.xaeroenhance.RegionIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.region.LeveledRegionManager;

/** Tells {@link RegionIndex} whenever the set of root regions changes, right after the change. */
@Mixin(LeveledRegionManager.class)
public abstract class LeveledRegionManagerMixin {
	@Inject(method = "putLeaf", at = @At(value = "INVOKE", target = "Ljava/util/HashMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
			shift = At.Shift.AFTER))
	private void xaeroEnhance$rootAdded(CallbackInfo ci) {
		RegionIndex.invalidate();
	}

	@Inject(method = "remove", at = @At(value = "INVOKE", target = "Ljava/util/HashMap;remove(Ljava/lang/Object;)Ljava/lang/Object;",
			shift = At.Shift.AFTER))
	private void xaeroEnhance$rootRemoved(CallbackInfoReturnable<Boolean> cir) {
		RegionIndex.invalidate();
	}
}
