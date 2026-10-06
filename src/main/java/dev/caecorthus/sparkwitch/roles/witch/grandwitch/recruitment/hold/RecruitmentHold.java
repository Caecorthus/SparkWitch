package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertTargeting;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Stable contract: the 5-second hold a freshly recruited accomplice spends next to the Grand Witch. While held the
 * recruit is invisible, invulnerable, blind, rooted to the anchor, and locked out of items, skills, interactions and
 * mouse look. The input lock is the Control Expert stun's ({@code ControlExpertStun.isStunned} also answers true while
 * held); the camera lock, kill and damage guards and held-item hiding are this module's own seams.
 * 稳定契约：新招募的共犯在大魔女身旁度过的 5 秒定身。定身期间被招募者隐身、无敌、失明、被固定在锚点，且无法使用
 * 物品、技能、交互与鼠标视角。输入锁复用控场专家眩晕（定身期间 {@code ControlExpertStun.isStunned} 同样返回 true）；
 * 视角锁、击杀与伤害拦截以及手持物隐藏是本模块自己的接缝。
 */
public final class RecruitmentHold {
    public static final int DURATION_TICKS = 100; // 5 s

    private static boolean registered;

    private RecruitmentHold() {
    }

    /**
     * Server only, idempotent: ends the hold on Wathe reset and at round end. Death, spectating and every other loss of
     * participation end it in the component's own tick.
     * 仅服务端，可重复调用：在 Wathe 重置与回合结束时结束定身。死亡、旁观及其他失去参与资格的情况由组件自身的 tick 结束。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ResetPlayer.EVENT.register(RecruitmentHold::release);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    release(player);
                }
            }
        });
    }

    /**
     * Server only. Call right after the recruit was teleported (and holds the new role); anchors at the current
     * position and rotation. The effects last exactly as long as the hold and expire on their own; vanilla keeps a
     * longer pre-existing effect of the same type. A player who is not a living participant is left alone.
     * 仅服务端。在被招募者完成传送（且已获得新职业）后立即调用；以当前位置与朝向作为锚点。效果持续时间与定身相同并
     * 自然到期；原版会保留同类型中更长的既有效果。不是存活参与者的玩家不受影响。
     */
    public static void apply(ServerPlayerEntity recruit) {
        if (!ControlExpertTargeting.isParticipant(recruit)) {
            SparkWitch.LOGGER.warn("Recruitment hold skipped: {} is not a living participant.",
                    recruit.getGameProfile().getName());
            return;
        }
        addHoldEffect(recruit, StatusEffects.INVISIBILITY, 0);
        addHoldEffect(recruit, StatusEffects.BLINDNESS, 0);
        addHoldEffect(recruit, StatusEffects.SLOWNESS, RecruitmentHoldRules.SLOWNESS_AMPLIFIER);
        // Cancel a raised knife, bow or grenade charge without a release: a release would fire the knife stab.
        // 取消举起的刀、弓或手雷蓄力而不触发松手：松手会触发刀击。
        recruit.clearActiveItem();
        recruit.closeHandledScreen();
        recruit.setVelocity(Vec3d.ZERO);
        recruit.velocityModified = true;
        recruit.fallDistance = 0.0F;
        RecruitmentHoldComponent.KEY.get(recruit).start(DURATION_TICKS, recruit.getX(), recruit.getY(), recruit.getZ(),
                recruit.getYaw(), recruit.getPitch());
    }

    /**
     * Both sides: the server reads the authoritative counter, every client the synced copy. A held player who stops
     * participating (death, spectator, Wraith, round end) is released at once.
     * 两端通用：服务端读取权威计时，各客户端读取同步副本。被定身者一旦不再参与（死亡、旁观、冤魂、对局结束）立即解除。
     */
    public static boolean isHeld(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        RecruitmentHoldComponent hold = RecruitmentHoldComponent.KEY.getNullable(player);
        return hold != null && hold.isActive() && ControlExpertTargeting.isParticipant(player);
    }

    /** Server only: ends the hold early; idempotent. The effects expire on their own. / 仅服务端：提前结束定身；可重复调用。效果自然到期。 */
    public static void release(@Nullable ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        RecruitmentHoldComponent hold = RecruitmentHoldComponent.KEY.getNullable(player);
        if (hold != null) {
            hold.clear();
        }
    }

    /**
     * Server only: whether a Wathe kill of {@code victim} is cancelled, regardless of {@code force}.
     * 仅服务端：是否取消对 {@code victim} 的 Wathe 击杀，无论是否强制。
     */
    public static boolean blocksKill(ServerPlayerEntity victim, @Nullable Identifier deathReason) {
        return isHeld(victim) && !RecruitmentHoldRules.piercesHold(deathReason);
    }

    /**
     * Server only: whether vanilla damage to {@code player} is cancelled. Sources that bypass vanilla invulnerability
     * ({@code /kill}, the void) still apply, matching {@link RecruitmentHoldRules#piercesHold}.
     * 仅服务端：是否取消对 {@code player} 的原版伤害。绕过原版无敌的伤害来源（{@code /kill}、虚空）仍然生效，
     * 与 {@link RecruitmentHoldRules#piercesHold} 一致。
     */
    public static boolean blocksDamage(ServerPlayerEntity player, DamageSource source) {
        return isHeld(player) && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /**
     * Server only: pulls a drifting recruit back to the anchor pose; within the threshold nothing is sent.
     * 仅服务端：把偏离的被招募者拉回锚点姿态；阈值以内不发送任何内容。
     */
    static void keepAtAnchor(ServerPlayerEntity player, RecruitmentHoldState state) {
        if (player.networkHandler == null || !state.drifted(player.getX(), player.getY(), player.getZ())) {
            return;
        }
        player.networkHandler.requestTeleport(state.anchorX(), state.anchorY(), state.anchorZ(),
                state.anchorYaw(), state.anchorPitch());
    }

    private static void addHoldEffect(ServerPlayerEntity recruit, RegistryEntry<StatusEffect> type, int amplifier) {
        recruit.addStatusEffect(new StatusEffectInstance(type, DURATION_TICKS, amplifier, false, false, false));
    }
}
