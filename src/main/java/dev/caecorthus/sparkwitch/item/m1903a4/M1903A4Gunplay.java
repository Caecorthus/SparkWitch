package dev.caecorthus.sparkwitch.item.m1903a4;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M1903A4 sound state machine, server side only. A rifle starts each round not reloading. The first round loaded opens
 * the bolt and starts a two second reload window; every loaded round seats with a sound 0.8 s later, and when the
 * window runs out without another round the bolt closes with the alternate bolt sound. Firing plays the shot and
 * schedules the bolt sound one second later; a reload started before that cancels it. Firing also ends the reload
 * window, so a shot never leaves a second bolt sound behind.
 * <p>
 * Lifecycle: the per-player state (server thread only) is dropped on disconnect, on the player's Wathe death
 * ({@code KillPlayer.AFTER}) and reset ({@code ResetPlayer}), on server stop, and as soon as nothing is pending; a
 * player who is no longer alive (or is a spectator) plays nothing and loses its queue.
 * <p>
 * M1903A4 的音效状态机（仅服务端）。每局步枪初始不在装填状态。装入第一发时开栓并开启两秒装填窗口；每装一发在 0.8 秒后
 * 播放压弹声；窗口到期没有新弹药时用另一条拉栓音关栓。开火播放枪声，并在 1 秒后播放拉栓声；若这之前已经开始装填，则取消
 * 这次拉栓。开火同时结束装填窗口，避免之后再冒出第二条关栓音。
 * <p>
 * 生命周期：每名玩家的状态（仅服务端线程）在断线、该玩家的 Wathe 死亡（{@code KillPlayer.AFTER}）与重置
 * （{@code ResetPlayer}）、服务器停止时清除，没有待播音效时也立即清除；不再存活（或为旁观者）的玩家不播放任何音效并丢弃其队列。
 */
public final class M1903A4Gunplay {
    /** 0.8 s: the round seats, then its sound. / 0.8 秒后压弹声。 */
    private static final int LOAD_SOUND_DELAY_TICKS = 16;
    /** 2 s reload window before the bolt closes with bolt_1. / 两秒装填窗口，超时用 bolt_1 关栓。 */
    static final int RELOAD_WINDOW_TICKS = 40;
    /** 1 s after a shot the bolt cycles. / 开火 1 秒后拉栓。 */
    private static final int BOLT_AFTER_SHOT_TICKS = 20;

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static boolean registered;

    private M1903A4Gunplay() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(M1903A4Gunplay::tick);
        // Fabric may fire DISCONNECT on the Netty thread; the map is only touched on the server thread.
        // Fabric 可能在 Netty 线程触发 DISCONNECT；该映射只在服务端线程访问。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            server.execute(() -> STATES.remove(uuid));
        });
        KillPlayer.AFTER.register((victim, killer, deathReason) -> reset(victim));
        ResetPlayer.EVENT.register(M1903A4Gunplay::reset);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
    }

    /**
     * Round start, death or reset: the rifle is not reloading and carries no pending sounds.
     * 开局、死亡或重置：不在装填状态，也没有待播音效。
     */
    public static void reset(@Nullable ServerPlayerEntity player) {
        if (player != null) {
            STATES.remove(player.getUuid());
        }
    }

    /** One round went in: open the bolt on the first one, refresh the window, seat this round 0.8 s later. */
    public static void onLoaded(ServerPlayerEntity player) {
        State state = state(player);
        long now = now(player);
        if (!state.reloading) {
            state.reloading = true;
            // Reloading started before the post-shot bolt: that bolt belongs to a bolt that is being opened now.
            // 在开火后的拉栓之前开始装填：那次拉栓已经随开栓作废。
            state.shotBoltDue = -1L;
            play(player, SparkWitchSounds.M1903A4_OPENBOLT, 0.8F);
        }
        state.reloadDeadline = now + RELOAD_WINDOW_TICKS;
        state.loadSoundsDue.add(now + LOAD_SOUND_DELAY_TICKS);
    }

    /** A round left the barrel: shot now, bolt in one second, reload window dropped. / 开火：枪声，1 秒后拉栓，结束装填窗口。 */
    public static void onFired(ServerPlayerEntity player) {
        State state = state(player);
        play(player, SparkWitchSounds.M1903A4_SHOOT, 1.0F);
        state.reloading = false;
        state.reloadDeadline = 0L;
        state.shotBoltDue = now(player) + BOLT_AFTER_SHOT_TICKS;
    }

    private static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, State>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, State> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (!keepsState(player != null, player != null && player.isAlive(),
                    player != null && player.isSpectator())) {
                iterator.remove();
                continue;
            }
            State state = entry.getValue();
            long now = now(player);
            if (state.reloading && now >= state.reloadDeadline) {
                state.reloading = false;
                play(player, SparkWitchSounds.M1903A4_BOLT_1, 0.8F);
            }
            if (state.shotBoltDue >= 0L && now >= state.shotBoltDue) {
                state.shotBoltDue = -1L;
                play(player, SparkWitchSounds.M1903A4_BOLT, 0.8F);
            }
            state.loadSoundsDue.removeIf(due -> {
                if (now < due) {
                    return false;
                }
                play(player, SparkWitchSounds.M1903A4_LOAD, 0.8F);
                return true;
            });
            if (state.idle()) {
                iterator.remove();
            }
        }
    }

    /**
     * Only an online, living, non-spectator player keeps a sound queue. / 只有在线、存活且非旁观的玩家保留音效队列。
     */
    static boolean keepsState(boolean online, boolean alive, boolean spectator) {
        return online && alive && !spectator;
    }

    private static State state(ServerPlayerEntity player) {
        return STATES.computeIfAbsent(player.getUuid(), id -> new State());
    }

    private static long now(ServerPlayerEntity player) {
        return player.getServerWorld().getTime();
    }

    private static void play(ServerPlayerEntity player, SoundEvent sound, float volume) {
        player.getServerWorld().playSound(null, player.getBlockPos(), sound, SoundCategory.PLAYERS, volume, 1.0F);
    }

    private static final class State {
        private boolean reloading;
        private long reloadDeadline;
        private long shotBoltDue = -1L;
        private final List<Long> loadSoundsDue = new ArrayList<>();

        boolean idle() {
            return M1903A4Gunplay.idle(reloading, shotBoltDue, loadSoundsDue.size());
        }
    }

    /** Nothing left to play: the entry can go. / 没有待播音效：条目可以移除。 */
    static boolean idle(boolean reloading, long shotBoltDue, int pendingLoadSounds) {
        return !reloading && shotBoltDue < 0L && pendingLoadSounds <= 0;
    }
}
