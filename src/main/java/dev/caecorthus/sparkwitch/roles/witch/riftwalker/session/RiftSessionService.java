package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateCloseReason;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateRecord;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Frozen (G0) server API of the "inside a Rift Gate" session (plan §6, §7): entry, hop, exit, forced exits, the
 * living-spectator body model and its loophole guards, the re-entry cooldown, the payload guard and voice mute. Every
 * C2S request is re-validated here on the server thread; the client never predicts entry. Read-only queries
 * ({@link #isInside}, {@link #currentGate}, {@link #occupantNames}) are implemented by G0 from
 * {@link RiftSessionComponent} so other packages can rely on them now. Owned by P2.
 * 「在裂隙门内」会话的冻结（G0）服务端 API（plan §6、§7）：进门、跳门、出门、强制出门、活着的旁观者模型及其漏洞防护、
 * 再次进门冷却、数据包拦截与语音静音。每个 C2S 请求都在服务端线程重新校验；客户端从不预测进门。只读查询
 * （{@link #isInside}、{@link #currentGate}、{@link #occupantNames}）由 G0 基于 {@link RiftSessionComponent} 实现，
 * 其他包现在即可依赖。归属 P2。
 */
public final class RiftSessionService {
    private static boolean registered;

    private RiftSessionService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // TODO(P2): lifecycle listeners (death, reset, role change, round edges, disconnect). / TODO(P2)：生命周期监听。
    }

    /**
     * Right-click entry request from {@link RiftGateEntity#interact} (D2). Returns the interaction result
     * ({@code SUCCESS} when the session began, {@code FAIL} with an actionbar reason otherwise). G0 stub: PASS.
     * 来自 {@link RiftGateEntity#interact} 的右键进门请求（D2）。返回交互结果（会话开始为 {@code SUCCESS}，否则
     * {@code FAIL} 并在动作栏说明原因）。G0 存根：PASS。
     */
    public static ActionResult tryEnter(ServerPlayerEntity player, RiftGateEntity gate) {
        // TODO(P2): eligibility (RiftGateUsers), fee, cooldown, body model, sync. / TODO(P2)：资格、入场费、冷却、本体模型、同步。
        return ActionResult.PASS;
    }

    /**
     * {@code sparkwitch:rift_hop}: {@code direction} is -1 (previous) or +1 (next) in the hop ring; anything else is
     * ignored. Stale {@code sessionId}s are ignored. G0 stub: no-op.
     * {@code sparkwitch:rift_hop}：{@code direction} 为 -1（上一扇）或 +1（下一扇）；其他值忽略。过期会话 id 忽略。G0 存根：空操作。
     */
    public static void handleHop(ServerPlayerEntity player, int sessionId, int direction) {
        // TODO(P2)
    }

    /** {@code sparkwitch:rift_exit}: always honoured for the current session (Shift). G0 stub: no-op. / 当前会话的出门请求。 */
    public static void handleExit(ServerPlayerEntity player, int sessionId) {
        // TODO(P2)
    }

    /**
     * Called by {@code RiftGateRegistry.close} after a record was removed: force-exit every occupant of that gate at
     * {@code removed.pos()} with {@link RiftExitReason#GATE_CLOSED} (cooldown when {@code reason.appliesExitCooldown()}),
     * and refresh other occupants' ring index/size. G0 stub: no-op.
     * 由 {@code RiftGateRegistry.close} 在记录移除后调用：在 {@code removed.pos()} 处以 {@link RiftExitReason#GATE_CLOSED}
     * 强制该门内所有人出门（{@code reason.appliesExitCooldown()} 时上冷却），并刷新其他门内玩家的环序号与环大小。G0 存根：空操作。
     */
    public static void onGateRemoved(ServerWorld world, RiftGateRecord removed, RiftGateCloseReason reason) {
        // TODO(P2)
    }

    /** Per-tick upkeep from {@link RiftSessionComponent#serverTick} (anchor, stay limit, eligibility). G0 stub: no-op. / 每 tick 维护。 */
    public static void tick(ServerPlayerEntity player) {
        // TODO(P2)
    }

    /** Ends the session (if any) for {@code reason}; returns whether one ended. G0 stub: false. / 结束会话；返回是否确有会话结束。 */
    public static boolean forceExit(ServerPlayerEntity player, RiftExitReason reason) {
        // TODO(P2)
        return false;
    }

    /** Both sides (the client knows only its own state). / 双端（客户端只知道自己的状态）。 */
    public static boolean isInside(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        RiftSessionComponent session = RiftSessionComponent.KEY.getNullable(player);
        return session != null && session.inside();
    }

    /** Server: the gate number the player is inside, if any. / 服务端：玩家所在门的编号（若在门内）。 */
    public static OptionalInt currentGate(ServerPlayerEntity player) {
        RiftSessionComponent session = player == null ? null : RiftSessionComponent.KEY.getNullable(player);
        return session != null && session.inside() ? OptionalInt.of(session.gateNumber()) : OptionalInt.empty();
    }

    /**
     * Server: profile names of the players currently inside gate {@code gateNumber} of this world, sorted (D16: the
     * console shows who is inside).
     * 服务端：当前位于本世界 {@code gateNumber} 号门内的玩家名，已排序（D16：控制台显示门内是谁）。
     */
    public static List<String> occupantNames(ServerWorld world, int gateNumber) {
        List<String> names = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            OptionalInt gate = currentGate(player);
            if (gate.isPresent() && gate.getAsInt() == gateNumber) {
                names.add(player.getGameProfile().getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }
}
