package mod.kelvinlby.xaeroenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;
import xaero.map.gui.GuiMap;

/**
 * Each time the previously requested region has loaded, the map screen requests the next few from the closest
 * unloaded ones it found this frame: 2 zoomed-out (branch) regions and 1 leaf region. The map thread finishes those
 * long before the next frame, so the screen fills in at about one region per frame. Requesting a few more per round
 * keeps it busy; Xaero's own cap on regions in flight still applies.
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapMixin {
	@Unique
	private static final int REGIONS_PER_REQUEST = 4;

	@ModifyConstant(method = "render", remap = true, constant = @Constant(intValue = 2, ordinal = 0),
			slice = @Slice(from = @At(value = "INVOKE", target = "Lxaero/map/MapProcessor;getAffectingLoadingFrequencyCount()I", remap = false)))
	private int xaeroEnhance$branchRequests(int original) {
		return REGIONS_PER_REQUEST;
	}

	@ModifyConstant(method = "render", remap = true, constant = @Constant(intValue = 1, ordinal = 0),
			slice = @Slice(from = @At(value = "INVOKE",
					target = "Lxaero/map/file/MapSaveLoad;requestBranchCache(Lxaero/map/region/BranchLeveledRegion;Ljava/lang/String;)V", remap = false)))
	private int xaeroEnhance$leafRequests(int original) {
		return REGIONS_PER_REQUEST;
	}
}
