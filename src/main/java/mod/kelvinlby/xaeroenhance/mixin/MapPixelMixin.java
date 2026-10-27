package mod.kelvinlby.xaeroenhance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mod.kelvinlby.xaeroenhance.AsyncBuffers;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.lib.client.level.block.BlockTextureColorUtils;
import xaero.map.region.MapPixel;

@Mixin(value = MapPixel.class, remap = false)
public abstract class MapPixelMixin {
	@WrapOperation(method = "getPixelColours",
			at = @At(value = "INVOKE", target = "Lxaero/lib/client/level/block/BlockTextureColorUtils;getBlockTextureColor(Lnet/minecraft/block/BlockState;ZLnet/minecraft/world/World;Lnet/minecraft/registry/Registry;Lnet/minecraft/util/math/BlockPos;)I",
					remap = true))
	private int xaeroEnhance$textureColor(BlockTextureColorUtils utils, BlockState state, boolean convert, World world, Registry<Block> registry,
			BlockPos pos, Operation<Integer> original) {
		if (!AsyncBuffers.isWorker()) {
			return original.call(utils, state, convert, world, registry, pos);
		}
		BlockPos immutablePos = pos.toImmutable();
		return AsyncBuffers.textureColor(utils, state, () -> original.call(utils, state, convert, world, registry, immutablePos));
	}
}
