package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.effect;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Server only: Blindness + Slowness II with hidden particles and a visible icon, shared by GW-DK and the TR fallback.
 * Vanilla merging keeps a longer or stronger existing effect. Wathe strips Blindness from everyone when a blackout
 * ends, so a running shell Blindness ends with it (known limitation).
 * 仅服务端：失明 + 缓慢 II，隐藏粒子、显示图标，由 GW-DK 与 TR 后备效果共用。原版合并会保留更长或更强的已有效果。
 * Wathe 在停电结束时会移除所有人的失明，炮弹造成的失明也会一并结束（已知限制）。
 */
final class PotionShellDebuff {
    private PotionShellDebuff() {
    }

    static void apply(ServerPlayerEntity target, int ticks, @Nullable Entity source) {
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, ticks,
                0, false, false, true), source);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks,
                PotionGunnerRules.SLOWNESS_II_AMPLIFIER, false, false, true), source);
    }
}
