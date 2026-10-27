package mod.kelvinlby.xaeroenhance.mixin;

import mod.kelvinlby.xaeroenhance.AsyncBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.lib.client.level.block.BlockTextureColorUtils;

@Mixin(value = BlockTextureColorUtils.class, remap = false)
public abstract class BlockTextureColorUtilsMixin {
	@Inject(method = "requestCachedColoursClear", at = @At("HEAD"))
	private void xaeroEnhance$clearWorkerCaches(CallbackInfo ci) {
		AsyncBuffers.clearColorCaches();
	}
}
