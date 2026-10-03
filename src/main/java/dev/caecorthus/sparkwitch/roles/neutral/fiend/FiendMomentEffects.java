package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-thread record of the Speed I instance the Fiend Moment granted; the moment grants no shield. Vanilla merges a
 * new effect into an existing instance of the same type, so ownership is claimed only when the moment's own grant
 * decided the result (Speed I for exactly the moment), and removal compares the live instance by identity: a foreign
 * or later-replaced effect is never removed.
 * 魔人时刻所授予的速度 I 实例的服务端线程登记表；时刻不授予护盾。原版会把新效果合并进同类型的已有实例，因此只有当时刻
 * 自身的授予决定了结果时（持续时间恰为整个时刻的速度 I）才登记归属；移除时按身份比较实时实例，他人的或之后被替换的
 * 效果永远不会被移除。
 */
final class FiendMomentEffects {
    private static final Map<UUID, StatusEffectInstance> OWNED_SPEED = new HashMap<>();

    private FiendMomentEffects() {
    }

    static void grant(ServerPlayerEntity fiend) {
        UUID id = fiend.getUuid();
        forget(id);

        fiend.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, FiendRules.MOMENT_DURATION_TICKS,
                FiendRules.MOMENT_SPEED_AMPLIFIER, false, false, true));
        StatusEffectInstance speed = fiend.getStatusEffect(StatusEffects.SPEED);
        if (speed != null && FiendMomentRules.claimsSpeed(speed.getAmplifier(), speed.getDuration())) {
            OWNED_SPEED.put(id, speed);
        }
    }

    /** Removes what is still exactly the moment's own, then forgets the player. / 移除仍完全属于时刻的效果，然后遗忘该玩家。 */
    static void release(ServerPlayerEntity player) {
        StatusEffectInstance speed = OWNED_SPEED.remove(player.getUuid());
        StatusEffectInstance liveSpeed = player.getStatusEffect(StatusEffects.SPEED);
        if (speed != null && liveSpeed == speed && liveSpeed.getAmplifier() == FiendRules.MOMENT_SPEED_AMPLIFIER) {
            player.removeStatusEffect(StatusEffects.SPEED);
        }
    }

    static void forget(UUID player) {
        OWNED_SPEED.remove(player);
    }

    static void forgetAll() {
        OWNED_SPEED.clear();
    }
}
