package mod.kelvinlby.xaeroenhance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mod.kelvinlby.xaeroenhance.AsyncBuffers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.lib.client.level.block.BlockTextureColorUtils;
import xaero.map.region.MapPixel;

@Mixin(MapPixel.class)
public abstract class MapPixelMixin {
	@WrapOperation(method = "getPixelColours",
			at = @At(value = "INVOKE", target = "Lxaero/lib/client/level/block/BlockTextureColorUtils;getBlockTextureColor(Lnet/minecraft/world/level/block/state/BlockState;ZLnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/core/Registry;Lnet/minecraft/core/BlockPos;)I"))
	private int xaeroEnhance$textureColor(BlockTextureColorUtils utils, BlockState state, boolean convert, ClientLevel world, Registry<Block> registry,
			BlockPos pos, Operation<Integer> original) {
		if (!AsyncBuffers.isWorker()) {
			return original.call(utils, state, convert, world, registry, pos);
		}
		BlockPos immutablePos = pos.immutable();
		return AsyncBuffers.textureColor(utils, state, () -> original.call(utils, state, convert, world, registry, immutablePos));
	}
}
