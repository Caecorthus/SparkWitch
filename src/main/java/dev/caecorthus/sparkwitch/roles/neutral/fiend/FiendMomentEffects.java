package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.ModEffects;
import org.agmas.noellesroles.effect.WhiskeyShieldEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-thread record of the Speed IV and whiskey-shield instances the Fiend Moment granted. Vanilla merges a new
 * effect into an existing instance of the same type, so ownership is claimed only when the moment's own grant decided
 * the result (Speed IV for exactly the moment, or a shield that was absent before), and removal compares the live
 * instance by identity: a foreign or later-replaced effect is never removed. The shield is one NoellesRoles whiskey
 * layer ({@code WhiskeyShieldEffect.addShieldLayer}), so it blocks one lethal hit or one Taotie swallow.
 * 魔人时刻所授予的速度 IV 与威士忌护盾实例的服务端线程登记表。原版会把新效果合并进同类型的已有实例，因此只有当时刻
 * 自身的授予决定了结果时（持续时间恰为整个时刻的速度 IV，或此前不存在的护盾）才登记归属；移除时按身份比较实时实例，
 * 他人的或之后被替换的效果永远不会被移除。护盾为 NoellesRoles 威士忌护盾一层（{@code WhiskeyShieldEffect.addShieldLayer}），
 * 可抵挡一次致命伤害或一次饕餮吞噬。
 */
final class FiendMomentEffects {
    private static final Map<UUID, StatusEffectInstance> OWNED_SPEED = new HashMap<>();
    private static final Map<UUID, StatusEffectInstance> OWNED_SHIELD = new HashMap<>();

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

        boolean shieldAbsent = !fiend.hasStatusEffect(ModEffects.WHISKEY_SHIELD);
        for (int layer = 0; layer < FiendRules.MOMENT_SHIELD_LAYERS; layer++) {
            WhiskeyShieldEffect.addShieldLayer(fiend, FiendRules.MOMENT_DURATION_TICKS);
        }
        StatusEffectInstance shield = fiend.getStatusEffect(ModEffects.WHISKEY_SHIELD);
        if (shieldAbsent && shield != null) {
            OWNED_SHIELD.put(id, shield);
        }
    }

    /** Removes what is still exactly the moment's own, then forgets the player. / 移除仍完全属于时刻的效果，然后遗忘该玩家。 */
    static void release(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        StatusEffectInstance speed = OWNED_SPEED.remove(id);
        StatusEffectInstance liveSpeed = player.getStatusEffect(StatusEffects.SPEED);
        if (speed != null && liveSpeed == speed && liveSpeed.getAmplifier() == FiendRules.MOMENT_SPEED_AMPLIFIER) {
            player.removeStatusEffect(StatusEffects.SPEED);
        }
        StatusEffectInstance shield = OWNED_SHIELD.remove(id);
        if (shield != null && player.getStatusEffect(ModEffects.WHISKEY_SHIELD) == shield) {
            player.removeStatusEffect(ModEffects.WHISKEY_SHIELD);
        }
    }

    static void forget(UUID player) {
        OWNED_SPEED.remove(player);
        OWNED_SHIELD.remove(player);
    }

    static void forgetAll() {
        OWNED_SPEED.clear();
        OWNED_SHIELD.clear();
    }
}
