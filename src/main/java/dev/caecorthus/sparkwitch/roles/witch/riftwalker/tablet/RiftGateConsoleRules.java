package dev.caecorthus.sparkwitch.roles.witch.riftwalker.tablet;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateRecord;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftGateConsoleS2CPacket;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

/**
 * Pure server rules of the Rift Gate console (D12, D16, C9; plan §15): the ordered access gates, the snapshot rows and
 * the tuning numbers of the poll/session/throttle protocol. No Minecraft state is read here so every rule is unit
 * tested; {@link RiftGateConsoleService} feeds it from the live player.
 * 裂隙门控制台的纯服务端规则（D12、D16、C9；plan §15）：有序的访问门槛、快照行，以及轮询/会话/节流协议的调参数值。
 * 这里不读取任何 Minecraft 状态，便于单元测试；由 {@link RiftGateConsoleService} 从实时玩家取值传入。
 */
public final class RiftGateConsoleRules {
    /**
     * The client polls an open console about once per second (refresh cadence of plan §15).
     * 控制台打开时客户端约每秒轮询一次（plan §15 的刷新频率）。
     */
    public static final int POLL_INTERVAL_TICKS = 20;
    /**
     * The server learns that a console closed when its polls stop: a session not polled for this long is dead and its
     * id can no longer close gates (5 polls of slack, so ordinary lag never expires an open console).
     * 服务端通过轮询停止得知控制台已关闭：超过该时长未轮询的会话视为失效，其 id 不能再关门（留 5 次轮询的余量，
     * 普通延迟不会让打开中的控制台失效）。
     */
    public static final int SESSION_TIMEOUT_TICKS = 100;
    /** Minimum spacing of two answered polls. / 两次应答轮询的最小间隔。 */
    public static final int SNAPSHOT_THROTTLE_TICKS = 10;
    /**
     * Minimum spacing of two open requests / two close actions by one player. Half the client's own spacing (10 ticks),
     * so clock jitter between client and server never drops a legitimate request.
     * 同一玩家两次打开请求 / 两次关门的最小间隔。只有客户端自身间隔（10 刻）的一半，客户端与服务端的时钟抖动不会丢弃
     * 正常请求。
     */
    public static final int OPEN_THROTTLE_TICKS = 5;
    public static final int CLOSE_THROTTLE_TICKS = 5;
    /** Hotbar slots 0–8: SparkStrength opens its tablet only from these. / 快捷栏 0–8：SparkStrength 只从这些格打开平板。 */
    public static final int HOTBAR_SIZE = 9;

    /** Close cue at the gate (plan §15: a small puff and sound nearby players notice). / 关门提示（plan §15：附近可见可闻）。 */
    public static final float CLOSE_SOUND_VOLUME = 0.7F;
    public static final float CLOSE_SOUND_PITCH = 1.3F;
    public static final int CLOSE_PARTICLE_COUNT = 28;
    public static final double CLOSE_PARTICLE_SPREAD_XZ = 0.35;
    public static final double CLOSE_PARTICLE_SPREAD_Y = 0.7;
    public static final double CLOSE_PARTICLE_SPEED = 0.06;
    /** Particle centre above the gate base (half the gate height). / 粒子中心高于门底部的距离（门高的一半）。 */
    public static final double CLOSE_CUE_LIFT = 1.0;

    /**
     * Suffixes of {@code message.sparkwitch.riftwalker.console.denied.<suffix>}. / 拒绝提示键的后缀。
     */
    public static final String DENY_BLOCKED = "blocked";
    public static final String DENY_NO_TABLET = "no_tablet";
    public static final String DENY_INSIDE = "inside";
    public static final String DENY_STUNNED = "stunned";
    public static final String DENY_CONTROLLED = "controlled";

    private RiftGateConsoleRules() {
    }

    /**
     * Ordered gate for viewing (open and every poll): round ACTIVE and the RAW role is exactly the Riftwalker, then not
     * inside a gate (checked before the participant test, whose spectator check would otherwise hide it), then a live
     * participant, then a SparkStrength tablet in hotbar slots 0–8. Returns null when allowed, otherwise a deny suffix.
     * Later suppliers run only after every earlier check passed.
     * 查看门槛（打开与每次轮询）：对局 ACTIVE 且原始职业恰为隙行者，然后不在门内（先于参与者的旁观判定，以免被其掩盖），
     * 然后是存活参与者，最后快捷栏 0–8 中有 SparkStrength 平板。允许时返回 null，否则返回拒绝后缀。
     * 只有前序条件全部通过才会调用后续的 supplier。
     */
    @Nullable
    public static String viewDenyReason(boolean roundActive, boolean riftwalker, BooleanSupplier insideGate,
                                        BooleanSupplier liveParticipant, BooleanSupplier tabletInHotbar) {
        if (!roundActive || !riftwalker) {
            return DENY_BLOCKED;
        }
        if (insideGate.getAsBoolean()) {
            return DENY_INSIDE;
        }
        if (!liveParticipant.getAsBoolean()) {
            return DENY_BLOCKED;
        }
        if (!tabletInHotbar.getAsBoolean()) {
            return DENY_NO_TABLET;
        }
        return null;
    }

    /**
     * Ordered gate for closing a gate: the view gate, then not Control-Expert-stunned, then not Kidnapper-controlled.
     * (The stun payload guard already drops {@code rift_gate_close}; this is the handler's own re-check.) Stun and
     * control only block the action: the console stays open.
     * 关门门槛：查看门槛，然后未被控场专家眩晕，然后未被绑架者控制。（眩晕数据包守卫已拦截 {@code rift_gate_close}；
     * 这里是处理器自身的复查。）眩晕与控制只阻止关门，控制台保持打开。
     */
    @Nullable
    public static String closeDenyReason(@Nullable String viewDenyReason, BooleanSupplier stunned,
                                         BooleanSupplier kidnapped) {
        if (viewDenyReason != null) {
            return viewDenyReason;
        }
        if (stunned.getAsBoolean()) {
            return DENY_STUNNED;
        }
        if (kidnapped.getAsBoolean()) {
            return DENY_CONTROLLED;
        }
        return null;
    }

    /** Whether a close denial leaves the console session open. / 关门被拒后是否保留控制台会话。 */
    public static boolean keepsConsole(String closeDenyReason) {
        return DENY_STUNNED.equals(closeDenyReason) || DENY_CONTROLLED.equals(closeDenyReason);
    }

    /**
     * Snapshot rows in registry (number) order: fixed number, gate position (bottom centre), facing and occupant names
     * (D16). The placer and match id never leave the server. The packet constructor caps rows and names.
     * 按登记表（编号）顺序生成快照行：固定编号、门的位置（底部中心）、朝向与门内玩家名（D16）。放置者与对局 id
     * 从不离开服务端。行数与名字由数据包构造器截断。
     */
    public static List<RiftGateConsoleS2CPacket.Entry> rows(List<RiftGateRecord> gates,
                                                             IntFunction<List<String>> occupantNames) {
        List<RiftGateConsoleS2CPacket.Entry> rows = new ArrayList<>(Math.min(gates.size(),
                RiftGateConsoleS2CPacket.MAX_ENTRIES));
        for (RiftGateRecord gate : gates) {
            if (rows.size() >= RiftGateConsoleS2CPacket.MAX_ENTRIES) {
                break;
            }
            rows.add(new RiftGateConsoleS2CPacket.Entry(gate.number(), gate.pos().x, gate.pos().y, gate.pos().z,
                    gate.facing(), occupantNames.apply(gate.number())));
        }
        return rows;
    }
}
