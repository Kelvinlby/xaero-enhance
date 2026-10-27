package mod.kelvinlby.xaeroenhance.xaeroplus;

import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import mod.kelvinlby.xaeroenhance.HighlightBufferAccess;
import mod.kelvinlby.xaeroenhance.HighlightMeshes;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaeroplus.feature.render.DrawContext;
import xaeroplus.feature.render.highlight.AbstractHighlightVertexBuffer;
import xaeroplus.feature.render.highlight.MultiColorHighlightColorFunction;

/**
 * Chunk highlight meshes are built on worker threads and uploaded on the render thread once ready; the previous
 * mesh keeps being drawn meanwhile. On the world map they are built at one quad per screen pixel at most.
 */
@Mixin(value = AbstractHighlightVertexBuffer.class, remap = false)
public abstract class AbstractHighlightVertexBufferMixin implements HighlightBufferAccess {
	@Shadow
	protected boolean stale;
	@Shadow
	@Nullable
	protected GpuBuffer vertexBuffer;
	@Shadow
	protected boolean flipped;
	@Shadow
	public long lastRefreshed;
	@Shadow
	public int indexCount;

	@Unique
	private CompletableFuture<HighlightMeshes.Mesh> xaeroEnhance$pending;
	@Unique
	private boolean xaeroEnhance$rebuild;
	@Unique
	private int xaeroEnhance$cellShift;

	@Override
	public void xaeroEnhance$refresh(DrawContext ctx, Long2LongMap highlights, int color, @Nullable MultiColorHighlightColorFunction colorFunction) {
		int cellShift = xaeroEnhance$cellShift(ctx);
		boolean changed = stale || flipped != ctx.worldmap() || cellShift != xaeroEnhance$cellShift;
		stale = false;
		lastRefreshed = System.currentTimeMillis();
		flipped = ctx.worldmap();
		xaeroEnhance$cellShift = cellShift;
		if (highlights.isEmpty() || colorFunction == null && color >>> 24 == 0) {
			((AbstractHighlightVertexBuffer) (Object) this).close();
			return;
		}
		if (xaeroEnhance$pending != null) {
			// Asked again only because the first mesh is not uploaded yet: nothing to rebuild.
			xaeroEnhance$rebuild |= changed;
			return;
		}
		xaeroEnhance$pending = HighlightMeshes.build(highlights, flipped, xaeroEnhance$cellShift, colorFunction);
	}

	@Inject(method = "preRender", at = @At("HEAD"))
	private void xaeroEnhance$upload(DrawContext ctx, Long2LongMap highlights, int color, CallbackInfo ci) {
		CompletableFuture<HighlightMeshes.Mesh> pending = xaeroEnhance$pending;
		if (pending == null || !pending.isDone()) {
			return;
		}
		xaeroEnhance$pending = null;
		HighlightMeshes.Mesh mesh = pending.join();
		if (mesh == null) {
			return; // build failed and was logged; the next invalidation retries
		}
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
		indexCount = mesh.vertexCount() / 4 * 6;
		if (mesh.vertexCount() > 0) {
			vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Chunk Highlight Buffer", GpuBuffer.USAGE_VERTEX, mesh.data());
		}
		mesh.free();
		if (xaeroEnhance$rebuild) {
			xaeroEnhance$rebuild = false;
			stale = true;
		}
	}

	@Inject(method = "needsRefresh", at = @At("RETURN"), cancellable = true)
	private void xaeroEnhance$refreshOnZoom(DrawContext ctx, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && xaeroEnhance$cellShift(ctx) != xaeroEnhance$cellShift) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void xaeroEnhance$dropPending(CallbackInfo ci) {
		if (xaeroEnhance$pending != null) {
			xaeroEnhance$pending.thenAccept(mesh -> {
				if (mesh != null) {
					mesh.free();
				}
			});
			xaeroEnhance$pending = null;
			xaeroEnhance$rebuild = false;
		}
	}

	@Unique
	private static int xaeroEnhance$cellShift(DrawContext ctx) {
		return ctx.worldmap() ? HighlightMeshes.cellShift(ctx.fboScale()) : 0;
	}
}
