package dev.caecorthus.sparkwitch.roles.witch.riftwalker.tablet;

import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStatusComponent;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateCloseReason;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateRecord;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateRegistry;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftGateConsoleRequestC2SPacket;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftGateConsoleS2CPacket;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftwalkerNetworking;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Server side of the Rift Gate console in the SparkStrength tablet (D12, D16, C9; plan §15, research 05 option c): only a
 * live Riftwalker (RAW role) with {@code sparkstrength:tablet} in hotbar slots 0–8 (registry id only; fail closed
 * without SparkStrength) may open it. Protocol: the client opener sends {@code rift_gate_console_request(OPEN)}; the
 * server answers with a {@code rift_gate_console} snapshot carrying a fresh console session id, and the open console
 * polls with that id about once per second. The server never pushes on its own except right after a close; it learns
 * that the console closed when the polls stop ({@link RiftGateConsoleRules#SESSION_TIMEOUT_TICKS}), and a dead session
 * can no longer close gates. Every request is re-validated here; the client only predicts. Any Riftwalker may close any
 * gate (forced multiple Riftwalkers share one network). Owned by P8.
 * SparkStrength 平板中的裂隙门控制台的服务端（D12、D16、C9；plan §15，调研 05 方案 c）：只有快捷栏 0–8 格持有
 * {@code sparkstrength:tablet}（仅按注册 id；未装 SparkStrength 时失败关闭）的存活隙行者（原始职业）才能打开。协议：
 * 客户端拦截器发送 {@code rift_gate_console_request(OPEN)}；服务端以携带新控制台会话 id 的 {@code rift_gate_console}
 * 快照应答，打开中的控制台约每秒用该 id 轮询一次。除关门后立即回推外，服务端从不主动推送；轮询停止
 * （{@link RiftGateConsoleRules#SESSION_TIMEOUT_TICKS}）即视为控制台已关闭，失效的会话不能再关门。每个请求都在此重新
 * 校验，客户端只做预测。任何隙行者都能关闭任何门（强制出现的多名隙行者共用一张门网）。归属 P8。
 */
public final class RiftGateConsoleService {
    static final String DENIED_PREFIX = "message.sparkwitch.riftwalker.console.denied.";
    static final String EXPIRED = "message.sparkwitch.riftwalker.console.expired";
    static final String GATE_GONE = "message.sparkwitch.riftwalker.console.gone";
    static final String GATE_CLOSED = "message.sparkwitch.riftwalker.console.closed";

    /** Server thread only (receivers and lifecycle events run there). / 仅服务端线程（接收器与生命周期事件都在此执行）。 */
    private static final RiftGateConsoleSessions SESSIONS = new RiftGateConsoleSessions();
    private static boolean registered;

    private RiftGateConsoleService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
    }

    /**
     * {@code sparkwitch:rift_gate_console_request}: {@code consoleSessionId == 0} asks to open a new console
     * ({@link #tryOpen}); any other value is a refresh poll for that console session, answered with a fresh snapshot
     * while it is still the player's live session and the player still qualifies (otherwise CLOSED). Polls of a
     * superseded or unknown session are ignored, so a late reply never closes a newer console.
     * {@code sparkwitch:rift_gate_console_request}：{@code consoleSessionId == 0} 表示请求打开新控制台（{@link #tryOpen}）；
     * 其他值为该控制台会话的刷新轮询：仍是玩家的有效会话且玩家仍有资格时回复最新快照（否则回复 CLOSED）。
     * 已被取代或未知会话的轮询一律忽略，迟到的回复绝不会关掉更新的控制台。
     */
    public static void handleRequest(ServerPlayerEntity player, int consoleSessionId) {
        if (player == null) {
            return;
        }
        if (consoleSessionId == RiftGateConsoleRequestC2SPacket.OPEN) {
            tryOpen(player);
            return;
        }
        UUID id = player.getUuid();
        switch (SESSIONS.poll(id, consoleSessionId, now(player))) {
            case IGNORE, THROTTLED -> {
            }
            case CLOSE -> sendClosed(player);
            case SNAPSHOT -> {
                if (viewDenyReason(player) != null) {
                    // Died, left the role or entered a gate: close silently; the client usually closed first.
                    // 死亡、失去职业或进门：静默关闭；客户端通常已先行关闭。
                    SESSIONS.end(id);
                    sendClosed(player);
                    return;
                }
                sendSnapshot(player, consoleSessionId);
            }
        }
    }

    /**
     * Validates and opens a console session, sending the first snapshot; returns whether it opened. A denial explains
     * itself on the action bar and answers CLOSED; a request inside the open throttle gets a silent CLOSED unless a live
     * console exists (that console must not be closed by it).
     * 校验并打开控制台会话，发送第一份快照；返回是否打开。被拒绝时在动作栏说明原因并回复 CLOSED；处于打开节流窗口内的
     * 请求静默回复 CLOSED，除非存在有效控制台（不能因此关掉它）。
     */
    public static boolean tryOpen(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        UUID id = player.getUuid();
        String denied = viewDenyReason(player);
        if (denied != null) {
            SESSIONS.end(id);
            actionBar(player, Text.translatable(DENIED_PREFIX + denied));
            sendClosed(player);
            return false;
        }
        long now = now(player);
        int session = SESSIONS.open(id, now);
        if (session == RiftGateConsoleSessions.NONE) {
            // Throttled: answer CLOSED silently so the client's pending request clears and the next use works.
            // 被节流：静默回复 CLOSED，让客户端的待处理请求结束，下一次使用即可生效。
            if (SESSIONS.current(id, now) == RiftGateConsoleSessions.NONE) {
                sendClosed(player);
            }
            return false;
        }
        sendSnapshot(player, session);
        return true;
    }

    /**
     * {@code sparkwitch:rift_gate_close} (sent after the client's two-step confirm): re-validates the player, the live
     * console session and the per-player throttle, then {@code RiftGateRegistry.close(world, n, CONSOLE)} — P2
     * force-exits the occupants there with the re-entry cooldown (C3) — plays a small close cue at the gate for nearby
     * players and answers with a refreshed snapshot. A number that no longer exists gets a message and a refresh.
     * {@code sparkwitch:rift_gate_close}（客户端二次确认后发送）：重新校验玩家、有效控制台会话与每玩家节流，然后调用
     * {@code RiftGateRegistry.close(world, n, CONSOLE)}——由 P2 让门内的人在该门处强制出门并上再入冷却（C3）——在门口为
     * 附近玩家播放小型关门提示，并回复刷新后的快照。编号已不存在时提示并刷新。
     */
    public static void handleClose(ServerPlayerEntity player, int consoleSessionId, int gateNumber) {
        if (player == null) {
            return;
        }
        UUID id = player.getUuid();
        String denied = RiftGateConsoleRules.closeDenyReason(viewDenyReason(player),
                () -> isStunned(player), () -> isKidnapped(player));
        if (denied != null) {
            actionBar(player, Text.translatable(DENIED_PREFIX + denied));
            if (!RiftGateConsoleRules.keepsConsole(denied)) {
                SESSIONS.end(id);
                sendClosed(player);
            }
            return;
        }
        switch (SESSIONS.checkClose(id, consoleSessionId, now(player))) {
            case STALE -> {
                actionBar(player, Text.translatable(EXPIRED));
                sendClosed(player);
                return;
            }
            case SUPERSEDED, THROTTLED -> {
                // The client spaces closes wider than the server throttle; a superseded console is already gone.
                // 客户端的关门间隔大于服务端节流；被取代的控制台已不存在。
                return;
            }
            case ALLOWED -> {
            }
        }
        ServerWorld world = player.getServerWorld();
        Optional<RiftGateRecord> record = gateNumber > 0 ? RiftGateRegistry.byNumber(world, gateNumber)
                : Optional.empty();
        if (record.isEmpty() || !RiftGateRegistry.close(world, gateNumber, RiftGateCloseReason.CONSOLE)) {
            actionBar(player, Text.translatable(GATE_GONE, gateNumber));
            sendSnapshot(player, consoleSessionId);
            return;
        }
        playCloseCue(world, record.get());
        actionBar(player, Text.translatable(GATE_CLOSED, gateNumber));
        sendSnapshot(player, consoleSessionId);
    }

    /**
     * View gate fed from the live player: round ACTIVE, RAW role exactly Riftwalker (never the Black Raven acting role),
     * not inside a gate, live participant (alive in Wathe, not spectator/creative, not an active Wraith — this also
     * excludes Taotie-swallowed and Last-Stand bodies), SparkStrength tablet in the hotbar.
     * 从实时玩家取值的查看门槛：对局 ACTIVE、原始职业恰为隙行者（绝不使用黑羽鸦的扮演职业）、不在门内、存活参与者
     * （Wathe 中存活、非旁观/创造、非激活冤魂——也排除了被饕餮吞下与背水一战中的身体）、快捷栏中有 SparkStrength 平板。
     */
    @Nullable
    static String viewDenyReason(ServerPlayerEntity player) {
        if (player.isDisconnected()) {
            return RiftGateConsoleRules.DENY_BLOCKED;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return RiftGateConsoleRules.viewDenyReason(
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                RiftwalkerRules.isRiftwalker(game.getRole(player)),
                () -> RiftSessionService.isInside(player),
                () -> GameFunctions.isPlayerPlayingAndAlive(player) && !player.isSpectator() && !player.isCreative()
                        && !WraithStateService.isActive(player),
                () -> RiftGateConsoleDevices.hasTabletInHotbar(player));
    }

    private static void sendSnapshot(ServerPlayerEntity player, int consoleSessionId) {
        ServerWorld world = player.getServerWorld();
        RiftwalkerNetworking.sendConsole(player, new RiftGateConsoleS2CPacket(consoleSessionId,
                RiftGateConsoleRules.rows(RiftGateRegistry.gates(world),
                        number -> RiftSessionService.occupantNames(world, number))));
    }

    private static void sendClosed(ServerPlayerEntity player) {
        RiftwalkerNetworking.sendConsole(player, RiftGateConsoleS2CPacket.closed());
    }

    /**
     * Counterplay cue (plan §15): a small reverse-portal puff and a power-down sound at the closed gate, heard nearby.
     * 反制提示（plan §15）：在被关闭的门处放出一小团反向传送门粒子和断能音效，附近可闻。
     */
    private static void playCloseCue(ServerWorld world, RiftGateRecord gate) {
        Vec3d pos = gate.pos();
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + RiftGateConsoleRules.CLOSE_CUE_LIFT, pos.z,
                RiftGateConsoleRules.CLOSE_PARTICLE_COUNT, RiftGateConsoleRules.CLOSE_PARTICLE_SPREAD_XZ,
                RiftGateConsoleRules.CLOSE_PARTICLE_SPREAD_Y, RiftGateConsoleRules.CLOSE_PARTICLE_SPREAD_XZ,
                RiftGateConsoleRules.CLOSE_PARTICLE_SPEED);
        world.playSound(null, pos.x, pos.y + RiftGateConsoleRules.CLOSE_CUE_LIFT, pos.z,
                SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.BLOCKS,
                RiftGateConsoleRules.CLOSE_SOUND_VOLUME, RiftGateConsoleRules.CLOSE_SOUND_PITCH);
    }

    private static boolean isStunned(ServerPlayerEntity player) {
        return ControlExpertStatusComponent.KEY.maybeGet(player)
                .map(ControlExpertStatusComponent::isStunned)
                .orElse(false);
    }

    private static boolean isKidnapped(ServerPlayerEntity player) {
        return KidnapperControlComponent.KEY.maybeGet(player)
                .map(KidnapperControlComponent::isControlled)
                .orElse(false);
    }

    /** Server tick counter: one clock for every world. / 服务器刻计数：所有世界共用一个时钟。 */
    private static long now(ServerPlayerEntity player) {
        return player.getServerWorld().getServer().getTicks();
    }

    private static void actionBar(ServerPlayerEntity player, Text text) {
        player.sendMessage(text, true);
    }
}
