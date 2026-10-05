package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.compat.cooldown.SparkWitchItemCooldownReleases;
import dev.caecorthus.sparkwitch.mixin.accessor.ServerItemCooldownManagerAccessor;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerItemCooldownManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server only: an explicit {@code remove} (SparkFactionAPI {@code clearCooldown}, Wathe's round reset) also releases
 * the SparkWitch timers that gate the same item. Expiry never calls {@code remove}, and a {@code remove} another mod
 * cancels at HEAD (SparkTraits' forced melee floor) never reaches TAIL, so neither releases anything.
 * 仅服务端：显式 {@code remove}（SparkFactionAPI {@code clearCooldown}、Wathe 开局重置）同时释放门控同一物品的
 * SparkWitch 计时。到期不会调用 {@code remove}；被其他模组在 HEAD 取消的 {@code remove}（SparkTraits 强制近战下限）
 * 到不了 TAIL，两者都不释放任何计时。
 */
@Mixin(ItemCooldownManager.class)
public abstract class ItemCooldownRemovalMixin {
    @Inject(method = "remove", at = @At("TAIL"))
    private void sparkwitch$releaseOwnTimers(Item item, CallbackInfo ci) {
        if ((Object) this instanceof ServerItemCooldownManager manager) {
            SparkWitchItemCooldownReleases.onRemoved(((ServerItemCooldownManagerAccessor) manager).sparkwitch$getPlayer(), item);
        }
    }
}
