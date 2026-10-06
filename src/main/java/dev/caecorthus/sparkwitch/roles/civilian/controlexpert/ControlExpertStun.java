package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold.RecruitmentHold;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Stun state reads (both sides) and the server-only application and release.
 * 眩晕状态读取（两端通用）以及仅服务端执行的施加与解除。
 */
public final class ControlExpertStun {
    private static final ControlExpertOwnedEffects<RegistryEntry<StatusEffect>, StatusEffectInstance> OWNED =
            new ControlExpertOwnedEffects<>();

    private ControlExpertStun() {
    }

    /**
     * Side-agnostic: the server reads the authoritative counter, the owner's client the synced copy. A stunned
     * player who stops participating (death, spectator, Wraith, round end) is released at once.
     * 两端通用：服务端读取权威计时，拥有者客户端读取同步副本。被眩晕者一旦不再参与
     * （死亡、旁观、冤魂、对局结束）立即解除。
     *
     * <p>Seam: the Grand Witch recruitment hold reuses this input lock, so a held recruit also reads as stunned here
     * (interaction guards, blocked payloads, role refusals, client key lock). Readers of
     * {@link ControlExpertStatusComponent} itself (status HUD, Seeker bridge, Rift console) never see the hold.
     * 接缝：大魔女招募定身复用此输入锁，因此被定身的新共犯在此同样视为被眩晕（交互拦截、受限数据包、职业拒绝、
     * 客户端按键锁）。直接读取 {@link ControlExpertStatusComponent} 的地方（状态 HUD、搜寻者桥接、裂隙控制台）
     * 永远看不到定身。</p>
     */
    public static boolean isStunned(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        if (RecruitmentHold.isHeld(player)) {
            return true;
        }
        ControlExpertStatusComponent status = ControlExpertStatusComponent.KEY.getNullable(player);
        return status != null && status.isStunned() && ControlExpertTargeting.isParticipant(player);
    }

    /**
     * Server only. Non-lethal and attributed to {@code ce}; callers have already passed targeting. The long Slowness I
     * tail goes in first so vanilla hides it under Slowness III and restores it when the stun ends.
     * 仅服务端。非致命且来源记为 {@code ce}；调用方已通过目标判定。先施加较长的缓慢 I 尾段，
     * 原版会将其隐藏在缓慢 III 之下，并在眩晕结束时恢复。
     */
    public static void apply(ServerPlayerEntity ce, ServerPlayerEntity target, int tailTicks) {
        boolean slownessAbsent = target.getStatusEffect(StatusEffects.SLOWNESS) == null;
        boolean blindnessAbsent = target.getStatusEffect(StatusEffects.BLINDNESS) == null;
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ControlExpertRules.STUN_TICKS + tailTicks,
                ControlExpertRules.TAIL_SLOWNESS_AMPLIFIER, false, false, true), ce);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ControlExpertRules.STUN_TICKS,
                ControlExpertRules.STUN_SLOWNESS_AMPLIFIER, false, false, true), ce);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, ControlExpertRules.STUN_TICKS,
                0, false, false, true), ce);
        // Record the live map objects after merging, and only for types this stun introduced.
        // 在合并后登记实时对象，且只登记本次眩晕新引入的效果类型。
        OWNED.record(target.getUuid(), StatusEffects.SLOWNESS, slownessAbsent,
                target.getStatusEffect(StatusEffects.SLOWNESS));
        OWNED.record(target.getUuid(), StatusEffects.BLINDNESS, blindnessAbsent,
                target.getStatusEffect(StatusEffects.BLINDNESS));
        ControlExpertStatusComponent.KEY.get(target).stun(ControlExpertRules.STUN_TICKS);
        // Cancel a raised knife, bow or grenade charge without a release: a release would fire the knife stab.
        // 取消举起的刀、弓或手雷蓄力而不触发松手：松手会触发刀击。
        target.clearActiveItem();
    }

    /**
     * Server only: clears both counters and removes only the effects this role still owns.
     * 仅服务端：清除两个计时，并只移除本职业仍拥有的效果。
     */
    public static void release(@Nullable ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ControlExpertStatusComponent status = ControlExpertStatusComponent.KEY.getNullable(player);
        if (status != null) {
            status.clear();
        }
        for (RegistryEntry<StatusEffect> type : OWNED.release(player.getUuid(), player::getStatusEffect)) {
            player.removeStatusEffect(type);
        }
    }

    /** Server stop: forget every owned record. / 服务器停止：遗忘全部归属记录。 */
    public static void forgetAll() {
        OWNED.clear();
    }
}
