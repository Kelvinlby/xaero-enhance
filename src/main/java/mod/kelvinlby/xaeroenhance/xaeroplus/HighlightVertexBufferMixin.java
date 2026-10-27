package mod.kelvinlby.xaeroenhance.xaeroplus;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import mod.kelvinlby.xaeroenhance.HighlightBufferAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaeroplus.feature.render.DrawContext;
import xaeroplus.feature.render.highlight.HighlightVertexBuffer;

@Mixin(value = HighlightVertexBuffer.class, remap = false)
public abstract class HighlightVertexBufferMixin {
	@Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
	private void xaeroEnhance$refreshAsync(DrawContext ctx, Long2LongMap highlights, int color, CallbackInfo ci) {
		((HighlightBufferAccess) this).xaeroEnhance$refresh(ctx, highlights, color, null);
		ci.cancel();
	}
}
