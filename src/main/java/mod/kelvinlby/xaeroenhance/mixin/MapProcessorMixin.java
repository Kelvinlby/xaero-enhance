package mod.kelvinlby.xaeroenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;

@Mixin(value = MapProcessor.class, remap = false)
public abstract class MapProcessorMixin {
	/**
	 * While the map camera is still, Xaero drops the per-frame upload budget and processes every pending texture
	 * in one frame, which is what stalls the render thread on a zoomed-out map. Buffers are built off-thread now,
	 * so the regular budget keeps up.
	 */
	@Redirect(method = "onRenderProcess", at = @At(value = "FIELD", target = "Lxaero/map/gui/GuiMap;noUploadingLimits:Z", opcode = 180))
	private boolean xaeroEnhance$keepUploadBudget(GuiMap guiMap) {
		return false;
	}
}
