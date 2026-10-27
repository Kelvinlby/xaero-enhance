package mod.kelvinlby.xaeroenhance;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import org.jetbrains.annotations.Nullable;
import xaeroplus.feature.render.DrawContext;
import xaeroplus.feature.render.highlight.MultiColorHighlightColorFunction;

/**
 * Mixed into XaeroPlus's {@code AbstractHighlightVertexBuffer}.
 */
public interface HighlightBufferAccess {
	/** Replaces the synchronous {@code refresh} of both highlight buffer types. */
	void xaeroEnhance$refresh(DrawContext ctx, Long2LongMap highlights, int color, @Nullable MultiColorHighlightColorFunction colorFunction);
}
