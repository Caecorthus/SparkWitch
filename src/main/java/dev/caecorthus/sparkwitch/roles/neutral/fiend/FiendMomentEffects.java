package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-thread record of the Speed instance the Fiend Moment granted (Speed II for the moment, Speed IV for each Dash);
 * the moment grants no shield. Vanilla merges a new effect into an existing instance of the same type: a stronger one
 * upgrades it in place and keeps the weaker one hidden underneath, so a Dash over the moment's Speed II stays the same
 * instance and falls back to Speed II after 10 s. Ownership is claimed only when the moment's own grant decided the
 * result (Speed II for exactly the moment, or Speed IV for exactly a Dash), and removal compares the live instance by
 * identity at one of those two levels: a foreign or later-replaced effect is never removed.
 * 魔人时刻所授予的速度实例的服务端线程登记表（时刻为速度 II，每次疾驰为速度 IV）；时刻不授予护盾。原版会把新效果
 * 合并进同类型的已有实例：更强的效果原地升级该实例，并把较弱的效果隐藏在其下，因此疾驰叠在时刻速度 II 上时仍是同一
 * 实例，10 秒后回落为速度 II。只有当时刻自身的授予决定了结果时（持续时间恰为整个时刻的速度 II，或恰为一次疾驰的
 * 速度 IV）才登记归属；移除时按身份比较实时实例且等级须为这两者之一，他人的或之后被替换的效果永远不会被移除。
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

    /** Grants one Dash and claims the merged instance when the Dash decided it. / 授予一次疾驰，并在疾驰决定合并结果时登记归属。 */
    static void grantDash(ServerPlayerEntity fiend) {
        fiend.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, FiendRules.DASH_DURATION_TICKS,
                FiendRules.DASH_SPEED_AMPLIFIER, false, false, true));
        StatusEffectInstance speed = fiend.getStatusEffect(StatusEffects.SPEED);
        if (speed != null && FiendMomentRules.claimsDash(speed.getAmplifier(), speed.getDuration())) {
            OWNED_SPEED.put(fiend.getUuid(), speed);
        }
    }

    /**
     * Removes what is still exactly the moment's own (with any Speed hidden under it), then forgets the player.
     * 移除仍完全属于时刻的效果（连同其下隐藏的速度效果），然后遗忘该玩家。
     */
    static void release(ServerPlayerEntity player) {
        StatusEffectInstance speed = OWNED_SPEED.remove(player.getUuid());
        StatusEffectInstance liveSpeed = player.getStatusEffect(StatusEffects.SPEED);
        if (speed != null && liveSpeed == speed && FiendMomentRules.isOwnedSpeedLevel(liveSpeed.getAmplifier())) {
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
