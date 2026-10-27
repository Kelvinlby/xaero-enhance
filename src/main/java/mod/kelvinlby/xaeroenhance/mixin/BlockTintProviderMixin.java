package mod.kelvinlby.xaeroenhance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mod.kelvinlby.xaeroenhance.AsyncBuffers;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.lib.client.level.block.BlockTextureColorUtils;
import xaero.map.biome.BlockTintProvider;

@Mixin(value = BlockTintProvider.class, remap = false)
public abstract class BlockTintProviderMixin {
	@WrapOperation(method = "getBiomeColor",
			at = @At(value = "INVOKE", target = "Lxaero/lib/client/level/block/BlockTextureColorUtils;getBlockTintIndex(Lnet/minecraft/block/BlockState;)I", remap = true))
	private int xaeroEnhance$tintIndex(BlockTextureColorUtils utils, BlockState state, Operation<Integer> original) {
		if (!AsyncBuffers.isWorker()) {
			return original.call(utils, state);
		}
		return AsyncBuffers.tintIndex(state, () -> original.call(utils, state));
	}
}
