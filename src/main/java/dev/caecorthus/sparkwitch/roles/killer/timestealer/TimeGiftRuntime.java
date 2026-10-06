package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Target-side Time Gift runtime (owner decision 2026-10-05), the curse in reverse on the same timeline
 * ({@link TimeTheftSchedule}): silent for 15 s, then Speed I-IV with a private chime at 15/20/25/30 s, and at 35 s
 * Speed V for {@link TimeStealerRules#GIFT_FINAL_SPEED_TICKS}, full sanity and a private "restored" line. The gift
 * then ends and its Speed simply runs out. Unlike the curse, nothing is ever removed early: a gift ends only by
 * finishing, or when the target stops playing, dies or the round ends, and Wathe already clears effects then. The
 * Timekeeper never lifts a gift, and nothing here depends on the giver still being online, alive or the Time Stealer.
 * 目标侧赠时运行时（所有者决定 2026-10-05），在同一时间轴（{@link TimeTheftSchedule}）上反向执行诅咒：静默 15 秒，
 * 之后 15/20/25/30 秒依次获得速度 I-IV 并听到私有钟声；35 秒获得持续 {@link TimeStealerRules#GIFT_FINAL_SPEED_TICKS}
 * 的速度 V、恢复全部理智并收到私有提示。随后赠时结束，其速度自然到期。与诅咒不同，这里从不提前移除任何效果：赠时只会
 * 因完成而结束，或在目标不再参与、死亡、对局结束时结束，而那时 Wathe 本就会清除效果。计时员从不解除赠时，这里的一切也
 * 不依赖赠予者是否仍在线、存活或仍是窃时者。
 */
public final class TimeGiftRuntime {
    static final String RESTORED_KEY = "message.sparkwitch.time_gift.restored";
    /** Chime pitch of stage 1, above the curse's, rising by {@link #CHIME_PITCH_STEP}. / 第 1 阶钟声音高，高于诅咒，逐阶升高。 */
    static final float CHIME_BASE_PITCH = 1.1F;
    static final float CHIME_PITCH_STEP = 0.15F;
    private static final float CHIME_VOLUME = 1.0F;
    private static final float FINAL_VOLUME = 1.0F;
    private static final float FINAL_PITCH = 1.5F;
    private static boolean warnedGive;

    private TimeGiftRuntime() {
    }

    /**
     * Starts a gift on {@code target}; false when refused: already gifted, the giver themself, the round is not ACTIVE,
     * no current match, or the target is not playing and alive. The Gift Watch service owns every other target rule.
     * Never throws.
     * 对目标开始赠时；被拒绝时返回 false：已在赠时中、目标是赠予者本人、对局不是 ACTIVE、没有当前对局，或目标未存活参与。
     * 其余目标规则由赠时怀表服务负责。绝不抛出异常。
     */
    public static boolean give(ServerPlayerEntity target, ServerPlayerEntity giver) {
        if (target == null || giver == null || target.getUuid().equals(giver.getUuid())) {
            return false;
        }
        try {
            ServerWorld world = target.getServerWorld();
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            UUID match = TimeStealerMatch.currentId();
            if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE || match == null
                    || !GameFunctions.isPlayerPlayingAndAlive(target)) {
                return false;
            }
            TimeGiftPlayerComponent state = TimeGiftPlayerComponent.KEY.get(target);
            return !state.isGifted() && state.start(giver.getUuid(), match, world.getTime());
        } catch (RuntimeException exception) {
            if (!warnedGive) {
                warnedGive = true;
                SparkWitch.LOGGER.warn("Time Stealer: refusing a time gift that failed to start", exception);
            }
            return false;
        }
    }

    /**
     * Called only while {@code state.isGifted()}. A stale match, a round that is no longer ACTIVE, or a target who is no
     * longer playing and alive ends the gift. Each newly due stage is applied exactly once; the final stage gives Speed
     * V and full sanity, then ends the gift.
     * 仅在 {@code state.isGifted()} 时调用。对局失配、对局不再 ACTIVE，或目标不再存活参与时结束赠时。每个新到期阶段恰好
     * 施加一次；最终阶段给予速度 V 与全部理智，然后结束赠时。
     */
    public static void tick(ServerPlayerEntity target, TimeGiftPlayerComponent state) {
        if (!state.isGifted()) {
            return;
        }
        ServerWorld world = target.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        UUID match = TimeStealerMatch.currentId();
        if (match == null || !match.equals(state.matchId())
                || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !GameFunctions.isPlayerPlayingAndAlive(target)) {
            state.clear();
            return;
        }
        TimeTheftSchedule.Step step = TimeTheftSchedule.step(state.stage(), Math.max(0L, world.getTime() - state.givenAt()));
        if (step.lethal()) {
            // Ends once: the gift is gone before its rewards land, so a re-entrant tick can never pay twice.
            // 只结束一次：奖励落地前赠时已清除，因此重入 tick 不会重复给予。
            state.clear();
            applySpeed(target, TimeStealerRules.GIFT_FINAL_AMPLIFIER, TimeStealerRules.GIFT_FINAL_SPEED_TICKS);
            PlayerMoodComponent.KEY.get(target).setMood(TimeStealerRules.GIFT_RESTORED_MOOD);
            playPrivately(target, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, FINAL_VOLUME, FINAL_PITCH);
            target.sendMessage(Text.translatable(RESTORED_KEY), true);
            return;
        }
        if (!step.hasStages()) {
            return;
        }
        for (int stage = step.firstStage(); stage <= step.lastStage(); stage++) {
            applySpeed(target, TimeTheftSchedule.amplifier(stage), TimeStealerRules.GIFT_SPEED_DURATION_TICKS);
            playPrivately(target, SparkWitchSounds.TIME_STEALER_CHIME, CHIME_VOLUME, chimePitch(stage));
        }
        state.setStage(step.lastStage());
    }

    /** Drops only the dead player's own gift; gifts they gave keep running. / 只清除死者自身的赠时；其赠出的赠时继续。 */
    public static void onDeath(ServerPlayerEntity dead) {
        reset(dead);
    }

    /** Ends this player's gift; its Speed is left to Wathe's own reset. / 结束该玩家的赠时；其速度交由 Wathe 自身的重置处理。 */
    public static void reset(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        TimeGiftPlayerComponent state = TimeGiftPlayerComponent.KEY.getNullable(player);
        if (state != null) {
            state.clear();
        }
    }

    /** Private chime pitch, rising stage by stage above the curse's. / 私有钟声音高，逐阶升高且高于诅咒。 */
    static float chimePitch(int stage) {
        return CHIME_BASE_PITCH + CHIME_PITCH_STEP * (Math.clamp(stage, 1, TimeTheftSchedule.LAST_SLOWNESS_STAGE) - 1);
    }

    /**
     * Speed with no particles or ambient flag (nearby players see nothing) and the icon shown to the target; vanilla
     * merges it with any foreign Speed in place, so nothing foreign is ever touched.
     * 无粒子、非环境效果的速度（附近玩家看不到任何迹象），目标可见图标；原版会与任何外来速度原地合并，因此从不触及外来效果。
     */
    private static void applySpeed(ServerPlayerEntity target, int amplifier, int durationTicks) {
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, durationTicks, amplifier, false, false, true));
    }

    /** One sound packet to this player only, never a world broadcast. / 只向该玩家本人发送一个音效包，绝不在世界中广播。 */
    private static void playPrivately(ServerPlayerEntity player, @Nullable SoundEvent sound, float volume, float pitch) {
        if (sound != null) {
            player.playSoundToPlayer(sound, SoundCategory.PLAYERS, volume, pitch);
        }
    }
}
