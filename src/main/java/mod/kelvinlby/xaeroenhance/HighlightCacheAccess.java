package mod.kelvinlby.xaeroenhance;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Mixed into XaeroPlus's {@code ChunkHighlightCacheDimensionHandler}.
 */
public interface HighlightCacheAccess {
	ResourceKey<Level> xaeroEnhance$dimension();
}
