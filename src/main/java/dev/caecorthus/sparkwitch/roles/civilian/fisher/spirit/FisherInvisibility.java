package dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Acquisition, not duration, proves ownership. Never merge into a pre-existing effect. Vanilla mutates an existing
 * instance on upgrade, so ANY foreign application relinquishes removal rights, even an equal/shorter no-op merge.
 * 归属由取得实例时确定，不靠时长猜测；从不合并进已有隐身。原版升级会原地修改实例，因此任何外部施加
 * （包括等长/更短的无效合并）都放弃移除权。混合来源的效果仅按原版自然到期。
 */
final class FisherInvisibility extends StatusEffectInstance {
    private final FisherEffectOwnership<StatusEffectInstance> ownership = new FisherEffectOwnership<>(this);

    private FisherInvisibility(int ticks) {
        super(StatusEffects.INVISIBILITY, ticks, 0, false, false, true);
    }

    @Override
    public boolean upgrade(StatusEffectInstance incoming) {
        ownership.relinquish();
        return super.upgrade(incoming);
    }

    static void ensure(ServerPlayerEntity player, FisherSpiritComponent component) {
        if (player.getStatusEffect(StatusEffects.INVISIBILITY) != null || component.remainingTicks() <= 0) {
            return;
        }
        FisherInvisibility effect = new FisherInvisibility(component.remainingTicks());
        player.addStatusEffect(effect);
        component.invisibility = player.getStatusEffect(StatusEffects.INVISIBILITY) == effect ? effect : null;
    }

    static void release(ServerPlayerEntity player, FisherSpiritComponent component) {
        FisherInvisibility owned = component.invisibility;
        component.invisibility = null;
        if (owned != null && owned.ownership.owns(player.getStatusEffect(StatusEffects.INVISIBILITY))) {
            player.removeStatusEffect(StatusEffects.INVISIBILITY);
        }
    }
}
