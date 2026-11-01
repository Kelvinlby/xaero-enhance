package mod.kelvinlby.xaeroenhance.mixin;

import mod.kelvinlby.xaeroenhance.FrameWork;
import mod.kelvinlby.xaeroenhance.MapScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void xaeroEnhance$runFrameWork(boolean tick, CallbackInfo ci) {
		MapScreen.update(((Minecraft) (Object) this).gui.screen() instanceof GuiMap);
		FrameWork.runFrame();
	}
}
