package mod.kelvinlby.xaeroenhance.mixin;

import mod.kelvinlby.xaeroenhance.FrameWork;
import mod.kelvinlby.xaeroenhance.MapScreen;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void xaeroEnhance$runFrameWork(boolean tick, CallbackInfo ci) {
		MapScreen.update(((MinecraftClient) (Object) this).currentScreen instanceof GuiMap);
		FrameWork.runFrame();
	}
}
