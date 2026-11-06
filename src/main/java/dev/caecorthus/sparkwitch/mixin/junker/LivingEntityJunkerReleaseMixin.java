package dev.caecorthus.sparkwitch.mixin.junker;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerLootSpend;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Server players only: compares a looted charged item's cooldown and USED stat across the release, so only a release
 * that really threw or shot spends it. Never changes the release itself.
 * 仅服务端玩家：比较搜刮所得蓄力物品松手前后的冷却与 USED 统计，只有真正投出或射出的松手才消耗。从不改变松手本身。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityJunkerReleaseMixin {
    @WrapMethod(method = "stopUsingItem")
    private void sparkwitch$spendReleasedRagpickerLoot(Operation<Void> original) {
        JunkerLootSpend.ReleaseProbe probe = (Object) this instanceof ServerPlayerEntity player
                ? JunkerLootSpend.beforeRelease(player) : null;
        original.call();
        if (probe != null) {
            JunkerLootSpend.afterRelease((ServerPlayerEntity) (Object) this, probe);
        }
    }
}
