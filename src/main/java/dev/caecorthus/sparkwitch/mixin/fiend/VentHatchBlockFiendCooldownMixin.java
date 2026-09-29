package dev.caecorthus.sparkwitch.mixin.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendMomentService;
import dev.doctor4t.wathe.block.VentHatchBlock;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fiend Moment crowbar on vent hatches: Wathe's {@code VentHatchBlock#onUse} (vanilla override, {@code remap = true})
 * writes the crowbar cooldown itself when the main hand holds a crowbar. The anchor is that branch's
 * {@code ActionResult.success(boolean)} (ordinal 0; the other success belongs to the crowbar-free branch), which runs
 * after both of Wathe's cooldown writes, including SparkTraits' Conscience {@code @Redirect} of the second one, so no
 * second redirect is added and the moment Fiend's exact 5 s write lands last. Server only; every other user returns
 * early.
 * 通风口上的魔人时刻撬棍：Wathe {@code VentHatchBlock#onUse}（原版覆写，{@code remap = true}）在主手持撬棍时自行写入撬棍冷却。
 * 锚点是该分支的 {@code ActionResult.success(boolean)}（序号 0；另一个 success 属于未持撬棍的分支），它位于 Wathe 两次冷却写入
 * 之后（包括 SparkTraits 良心对第二次写入的 {@code @Redirect}），因此不新增第二个重定向，时刻中魔人的精确 5 秒写入最后生效。
 * 仅服务端；其他使用者一律提前返回。
 */
@Mixin(VentHatchBlock.class)
public abstract class VentHatchBlockFiendCooldownMixin {
    @Inject(
            method = "onUse(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/ActionResult;success(Z)Lnet/minecraft/util/ActionResult;",
                    ordinal = 0))
    private void sparkwitch$fiendMomentVentCrowbarCooldown(BlockState state, World world, BlockPos pos,
                                                           PlayerEntity player, BlockHitResult hit,
                                                           CallbackInfoReturnable<ActionResult> cir) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        FiendMomentService.applyCrowbarCooldown(serverPlayer, WatheItems.CROWBAR);
    }
}
