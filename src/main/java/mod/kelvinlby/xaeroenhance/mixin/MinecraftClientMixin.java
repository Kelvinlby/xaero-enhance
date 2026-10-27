package mod.kelvinlby.xaeroenhance.mixin;

import mod.kelvinlby.xaeroenhance.FrameWork;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void xaeroEnhance$runFrameWork(boolean tick, CallbackInfo ci) {
		FrameWork.runFrame();
	}
}
