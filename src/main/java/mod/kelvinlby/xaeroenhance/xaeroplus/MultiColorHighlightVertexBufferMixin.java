package mod.kelvinlby.xaeroenhance.xaeroplus;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import mod.kelvinlby.xaeroenhance.HighlightBufferAccess;
import mod.kelvinlby.xaeroenhance.MapScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaeroplus.feature.render.DrawContext;
import xaeroplus.feature.render.highlight.MultiColorHighlightColorFunction;
import xaeroplus.feature.render.highlight.MultiColorHighlightVertexBuffer;

@Mixin(value = MultiColorHighlightVertexBuffer.class, remap = false)
public abstract class MultiColorHighlightVertexBufferMixin {
	@Shadow
	@Final
	private MultiColorHighlightColorFunction colorFunction;

	@Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
	private void xaeroEnhance$refreshAsync(DrawContext ctx, Long2LongMap highlights, int color, CallbackInfo ci) {
		if (!MapScreen.isOpen() || !ctx.worldmap()) {
			return; // the minimap keeps XaeroPlus's own refresh
		}
		((HighlightBufferAccess) this).xaeroEnhance$refresh(ctx, highlights, color, colorFunction);
		ci.cancel();
	}
}
