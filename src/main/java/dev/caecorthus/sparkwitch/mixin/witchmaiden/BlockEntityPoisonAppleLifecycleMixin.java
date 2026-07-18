package dev.caecorthus.sparkwitch.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateAccess;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateService;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateTracker;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loaded-entity lifecycle adapter: reject old-match NBT after world attachment and keep the weak index loaded-only.
 * 方块实体接入世界后拒绝旧局 NBT，并确保弱索引只包含已加载餐盘。
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityPoisonAppleLifecycleMixin {
    @Inject(method = "setWorld", at = @At("TAIL"))
    private void sparkwitch$validateLoadedPoisonApple(World world, CallbackInfo ci) {
        if (!world.isClient && (Object) this instanceof PoisonApplePlateAccess poisonApple) {
            PoisonApplePlateService.refreshMatch(poisonApple);
        }
    }

    @Inject(method = "markRemoved", at = @At("TAIL"))
    private void sparkwitch$untrackUnloadedPoisonApple(CallbackInfo ci) {
        if ((Object) this instanceof PoisonApplePlateAccess poisonApple) {
            PoisonApplePlateTracker.untrack(poisonApple);
        }
    }

    @Inject(method = "cancelRemoval", at = @At("TAIL"))
    private void sparkwitch$restoreReloadedPoisonAppleTracking(CallbackInfo ci) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (blockEntity.getWorld() != null
                && !blockEntity.getWorld().isClient
                && (Object) this instanceof PoisonApplePlateAccess poisonApple
                && poisonApple.sparkwitch$isPoisonAppleArmed()) {
            PoisonApplePlateTracker.track(poisonApple);
            PoisonApplePlateService.refreshMatch(poisonApple);
        }
    }
}
