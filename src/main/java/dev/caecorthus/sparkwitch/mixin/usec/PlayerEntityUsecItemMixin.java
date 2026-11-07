package dev.caecorthus.sparkwitch.mixin.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecDeathDrops;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecLoadoutService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two hooks on the player drop method, which {@code ServerPlayerEntity} reaches through {@code super}, so every server
 * drop path is covered.
 * <ul>
 *   <li>HEAD: a USEC bound item dropped by a dead player (vanilla's inventory drop on death, which Wathe's
 *   KEEP_INVENTORY normally skips) is cancelled, so it vanishes ({@code UsecLoadoutService#vanishesOnDeath}). Every
 *   drop by a living player follows Wathe's own rules (owner, 2026-11-05).</li>
 *   <li>RETURN: a revolver item entity created for a USEC is recorded for the same-tick death-revolver dedup in
 *   {@code UsecDeathDrops#noteDrop}. A drop another mod cancels at HEAD never reaches RETURN.</li>
 * </ul>
 * 玩家丢弃方法上的两个钩子；{@code ServerPlayerEntity} 通过 {@code super} 调用此方法，因此覆盖所有服务端丢弃路径。
 * HEAD：已死亡玩家丢出的 USEC 绑定物品（原版死亡时的背包掉落，Wathe 的 KEEP_INVENTORY 通常会跳过它）被取消，因此直接消失
 * （{@code UsecLoadoutService#vanishesOnDeath}）。存活玩家的一切丢弃都遵循 Wathe 自身规则（所有者 2026-11-05）。
 * RETURN：为 USEC 生成的左轮物品实体会被记录，供 {@code UsecDeathDrops#noteDrop} 做同刻死亡左轮去重。被其他模组在 HEAD
 * 取消的丢弃不会到达 RETURN。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityUsecItemMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"),
            cancellable = true)
    private void sparkwitch$vanishUsecItemOnDeath(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (UsecLoadoutService.vanishesOnDeath((PlayerEntity) (Object) this, stack)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("RETURN"))
    private void sparkwitch$noteUsecRevolverDrop(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        UsecDeathDrops.noteDrop((PlayerEntity) (Object) this, cir.getReturnValue());
    }
}
