package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Victim-side curse runtime: starts a theft, advances stages from the victim's own component tick, settles the lethal
 * stage once through {@code TimeStealerKillService}, and lifts curses (Timekeeper purge, death, reset).
 * Server authority: the stage comes from absolute world ticks and the unsynced {@code sparkwitch:time_theft}
 * component; the victim only perceives vanilla Slowness (no particles) and chimes sent to their client alone, and
 * nothing here depends on the Time Stealer still being online, alive, or the Time Stealer (owner decision Q7).
 * 受害者侧诅咒运行时：开始窃取、由受害者自身组件 tick 推进阶段、经 {@code TimeStealerKillService} 对致死阶段结算一次，
 * 并解除诅咒（计时员清除、死亡、重置）。服务端权威：阶段来自绝对世界 tick 与不同步的 {@code sparkwitch:time_theft}
 * 组件；受害者只能感知原版缓慢（无粒子）与只发给其本人客户端的钟声，且这里的一切都不依赖窃时者是否仍在线、存活或仍是
 * 窃时者（所有者决定 Q7）。
 */
public final class TimeTheftRuntime {
    private static final float CHIME_VOLUME = 1.0F;
    private static final float FINAL_VOLUME = 1.0F;
    private static final float RETURNED_VOLUME = 0.6F;
    private static final float RETURNED_PITCH = 1.2F;
    private static boolean warnedPurge;
    private static boolean warnedSteal;
    /**
     * The latest Timekeeper purge (match id and world tick), so a victim who was offline during it and reconnects in
     * the same match is lifted on their first tick back instead of dying to a curse the Timekeeper already undid (Q5).
     * Server thread only; forgotten at initialize and finalize, and a different match never matches it anyway.
     * 最近一次计时员清除（对局 id 与世界 tick），使清除时离线、同局重连的受害者在回来的第一 tick 被解除，而不是死于
     * 计时员已经解除的诅咒（Q5）。仅服务器线程访问；在初始化与局末时遗忘，且不同对局本来就不会匹配。
     */
    private static @Nullable PurgeMark lastTimekeeperPurge;

    private TimeTheftRuntime() {
    }

    /**
     * Starts a curse on {@code victim}; false when refused: already stolen (no re-steal), the round is not ACTIVE, no
     * current match, or the victim is not playing and alive. The Clock service owns every other target rule. Never
     * throws.
     * 对受害者开始诅咒；被拒绝时返回 false：已被窃（不可重复窃取）、对局不是 ACTIVE、没有当前对局，或受害者未存活参与。
     * 其余目标规则由时钟服务负责。绝不抛出异常。
     */
    public static boolean steal(ServerPlayerEntity victim, ServerPlayerEntity stealer) {
        if (victim == null || stealer == null || victim.getUuid().equals(stealer.getUuid())) {
            return false;
        }
        try {
            ServerWorld world = victim.getServerWorld();
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            UUID match = TimeStealerMatch.currentId();
            if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE || match == null
                    || !GameFunctions.isPlayerPlayingAndAlive(victim)) {
                return false;
            }
            TimeTheftPlayerComponent state = TimeTheftPlayerComponent.KEY.get(victim);
            return !state.isStolen() && state.start(stealer.getUuid(), match, world.getTime());
        } catch (RuntimeException exception) {
            if (!warnedSteal) {
                warnedSteal = true;
                SparkWitch.LOGGER.warn("Time Stealer: refusing a theft that failed to start", exception);
            }
            return false;
        }
    }

    /**
     * Called only while {@code state.isStolen()}. A stale match, a round that is no longer ACTIVE, or a victim who is
     * no longer playing and alive clears the curse (and only our Slowness); so does a Timekeeper purge that ran while
     * the victim was offline. Each newly due stage is applied exactly
     * once; at the lethal stage the final chime plays, the component and our Slowness are cleared first, and the kill
     * is settled once with no retry.
     * 仅在 {@code state.isStolen()} 时调用。对局失配、对局不再 ACTIVE，或受害者不再存活参与时清除诅咒（且只移除我们的缓慢）；
     * 受害者离线期间发生的计时员清除同样会解除诅咒。
     * 每个新到期阶段恰好施加一次；到达致死阶段时先响终钟，先清除组件与我们的缓慢，再结算一次击杀，不重试。
     */
    public static void tick(ServerPlayerEntity victim, TimeTheftPlayerComponent state) {
        if (!state.isStolen()) {
            return;
        }
        ServerWorld world = victim.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        UUID match = TimeStealerMatch.currentId();
        if (match == null || !match.equals(state.matchId())
                || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !GameFunctions.isPlayerPlayingAndAlive(victim)) {
            clear(victim, state);
            return;
        }
        PurgeMark purge = lastTimekeeperPurge;
        if (purge != null && liftedByPurge(state.matchId(), state.stolenAt(), purge.matchId(), purge.tick())) {
            boolean heardChime = state.stage() >= 1;
            clear(victim, state);
            if (heardChime) {
                announceTimeReturned(victim);
            }
            return;
        }
        TimeTheftSchedule.Step step = TimeTheftSchedule.step(state.stage(), elapsed(world, state));
        if (step.lethal()) {
            UUID stealerUuid = state.stealer();
            playPrivately(victim, SparkWitchSounds.TIME_STEALER_FINAL, FINAL_VOLUME, TimeTheftSchedule.FINAL_PITCH);
            // Settle once: the curse is gone before the kill runs, so a veto, a Judge block, or a re-entrant tick can
            // never settle it again. / 只结算一次：击杀前诅咒已清除，否决、审判者拦截或重入 tick 都无法再次结算。
            clear(victim, state);
            if (stealerUuid != null) {
                TimeStealerKillService.settle(victim, stealerUuid);
            }
            return;
        }
        if (!step.hasStages()) {
            return;
        }
        boolean owned = false;
        for (int stage = step.firstStage(); stage <= step.lastStage(); stage++) {
            owned = TimeTheftSlowness.apply(victim, stage);
            playPrivately(victim, SparkWitchSounds.TIME_STEALER_CHIME, CHIME_VOLUME, TimeTheftSchedule.chimePitch(stage));
        }
        // Only the last applied stage's node can still be ours. / 只有最近施加阶段的节点仍可能属于我们。
        state.setStage(step.lastStage());
        state.setSlownessOwned(owned);
    }

    /**
     * Lifts every curse in {@code world}, grace period included and whoever cast it (alive, dead, offline, or no longer
     * the Time Stealer). Idempotent and fault tolerant per player; changes no cooldown, stamp, or round time. For a
     * Timekeeper purge, victims who already heard a chime get a private actionbar line and a soft sound, grace-period
     * victims are cleared silently, and each affected online stealer is told once; the purge is also remembered so a
     * victim who was offline is lifted when they return in the same match. A reset purge is silent.
     * 解除 {@code world} 内所有诅咒，含静默期，无论施咒者是谁（存活、死亡、离线或已不是窃时者）。幂等，且逐个玩家容错；
     * 不改变任何冷却、邮票或对局时间。计时员清除时，已听过钟声的受害者收到私有 actionbar 提示与一声轻音，静默期受害者静默解除，
     * 每名受影响的在线窃时者只收到一次通知；清除还会被记住，使离线受害者在同局返回时被解除。重置清除不发任何提示。
     */
    public static int purgeAll(ServerWorld world, PurgeCause cause) {
        if (world == null) {
            return 0;
        }
        boolean notify = cause == PurgeCause.TIMEKEEPER;
        if (notify) {
            UUID match = TimeStealerMatch.currentId();
            lastTimekeeperPurge = match == null ? null : new PurgeMark(match, world.getTime());
        }
        int lifted = 0;
        Set<UUID> stealers = new LinkedHashSet<>();
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            try {
                TimeTheftPlayerComponent state = TimeTheftPlayerComponent.KEY.getNullable(player);
                if (state == null || !state.isStolen()) {
                    continue;
                }
                UUID stealerUuid = state.stealer();
                boolean heardChime = state.stage() >= 1;
                clear(player, state);
                lifted++;
                if (stealerUuid != null) {
                    stealers.add(stealerUuid);
                }
                if (notify && heardChime) {
                    announceTimeReturned(player);
                }
            } catch (RuntimeException exception) {
                warnPurge(exception);
            }
        }
        if (notify) {
            notifyStealers(world.getServer(), stealers);
        }
        return lifted;
    }

    /**
     * Whether a curse predates the latest Timekeeper purge of its own match. Strictly earlier: a theft committed later in
     * the purge's own tick was cast after the rescue (every curse cast earlier on that tick was lifted directly).
     * 诅咒是否早于其所属对局最近一次计时员清除。严格早于：在清除同一 tick 稍后提交的窃取发生在解救之后
     * （该 tick 更早施下的诅咒已被直接解除）。
     */
    static boolean liftedByPurge(@Nullable UUID curseMatch, long stolenAt, @Nullable UUID purgeMatch, long purgeTick) {
        return purgeMatch != null && purgeMatch.equals(curseMatch) && stolenAt < purgeTick;
    }

    /** Forgets the latest Timekeeper purge (initialize, finalize). / 遗忘最近一次计时员清除（初始化、局末）。 */
    public static void forgetTimekeeperPurge() {
        lastTimekeeperPurge = null;
    }

    /** Drops only the dead player's own curse; curses they cast keep running (Q7). / 只清除死者自身的诅咒；其施下的诅咒继续（Q7）。 */
    public static void onDeath(ServerPlayerEntity dead) {
        reset(dead);
    }

    /** Clears this player's curse and the Slowness it owns; silent and safe when not stolen. / 清除该玩家的诅咒及其拥有的缓慢；静默，未被窃时无副作用。 */
    public static void reset(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        TimeTheftPlayerComponent state = TimeTheftPlayerComponent.KEY.getNullable(player);
        if (state != null && state.isStolen()) {
            clear(player, state);
        }
    }

    /**
     * Removes only our Slowness node (computed from the stage, elapsed time and ownership before they are cleared),
     * then clears the component. / 先按清除前的阶段、经过时间与归属只移除我们的缓慢节点，再清空组件。
     */
    private static void clear(ServerPlayerEntity victim, TimeTheftPlayerComponent state) {
        int stage = state.stage();
        long elapsed = elapsed(victim.getServerWorld(), state);
        boolean owned = state.slownessOwned();
        state.clear();
        if (stage >= 1) {
            TimeTheftSlowness.removeOwned(victim, stage, elapsed, owned);
        }
    }

    private static long elapsed(ServerWorld world, TimeTheftPlayerComponent state) {
        return Math.max(0L, world.getTime() - state.stolenAt());
    }

    /** Private "time returned" line and soft sound. / 私有的“时间归还”提示与一声轻音。 */
    private static void announceTimeReturned(ServerPlayerEntity player) {
        player.sendMessage(Text.translatable("message.sparkwitch.time_theft.time_returned"), true);
        playPrivately(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, RETURNED_VOLUME, RETURNED_PITCH);
    }

    /**
     * One sound packet to this player only, at their own position (never a world broadcast, which would reveal the
     * curse to anyone nearby). / 只向该玩家本人在其自身位置发送一个音效包（绝不在世界中广播，否则附近的人都会察觉诅咒）。
     */
    private static void playPrivately(ServerPlayerEntity player, @Nullable SoundEvent sound, float volume, float pitch) {
        if (sound != null) {
            player.playSoundToPlayer(sound, SoundCategory.PLAYERS, volume, pitch);
        }
    }

    private static void notifyStealers(@Nullable MinecraftServer server, Set<UUID> stealers) {
        if (server == null) {
            return;
        }
        for (UUID stealerUuid : stealers) {
            try {
                ServerPlayerEntity stealer = server.getPlayerManager().getPlayer(stealerUuid);
                if (stealer != null) {
                    stealer.sendMessage(Text.translatable("message.sparkwitch.time_stealer.theft_undone"), true);
                }
            } catch (RuntimeException exception) {
                warnPurge(exception);
            }
        }
    }

    private static void warnPurge(RuntimeException exception) {
        if (!warnedPurge) {
            warnedPurge = true;
            SparkWitch.LOGGER.warn("Time Stealer: a curse purge step failed; continuing with the other players",
                    exception);
        }
    }

    private record PurgeMark(UUID matchId, long tick) {
    }

    /** Why curses are being lifted. / 解除诅咒的原因。 */
    public enum PurgeCause {
        TIMEKEEPER
    }
}
