package mod.kelvinlby.xaeroenhance.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import xaero.map.region.LeveledRegion;

@Mixin(LeveledRegion.class)
public interface LeveledRegionInvoker {
	@Invoker("get")
	LeveledRegion<?> xaeroEnhance$get(int x, int z, int level);
}
