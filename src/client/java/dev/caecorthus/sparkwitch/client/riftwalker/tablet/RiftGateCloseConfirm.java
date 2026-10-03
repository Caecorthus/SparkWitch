package dev.caecorthus.sparkwitch.client.riftwalker.tablet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Pure two-step close confirm of the console rows (plan §15, C9): the first click arms one row (「确认关闭？」 for
 * {@link RiftGateConsoleClientRules#CONFIRM_WINDOW_TICKS}), a second click on the same armed row (not sooner than
 * {@link RiftGateConsoleClientRules#CONFIRM_MIN_DELAY_TICKS}, so a double-click never closes) confirms it, which
 * greys the row (「关闭中…」) until the next snapshot drops the gate or {@link RiftGateConsoleClientRules#CLOSING_TIMEOUT_TICKS}
 * pass. Only one row is armed at a time; arming another row disarms the first. Presentation only: the server re-validates
 * the close.
 * 控制台各行的纯两步关门确认（plan §15、C9）：第一次点击让一行进入待确认（「确认关闭？」持续
 * {@link RiftGateConsoleClientRules#CONFIRM_WINDOW_TICKS}），再次点击同一行（不早于
 * {@link RiftGateConsoleClientRules#CONFIRM_MIN_DELAY_TICKS}，双击不会误关）即确认，此后该行变灰（「关闭中…」），直到下一份
 * 快照移除该门或经过 {@link RiftGateConsoleClientRules#CLOSING_TIMEOUT_TICKS}。同一时间只有一行待确认；点另一行会取消
 * 前一行。仅用于展示：关门由服务端重新校验。
 */
public final class RiftGateCloseConfirm {
    /** Result of one click. / 一次点击的结果。 */
    public enum Click {
        /** The row now asks for confirmation. / 该行进入待确认。 */
        ARMED,
        /** Second click inside the window: send the close. / 窗口内的第二次点击：发送关门请求。 */
        CONFIRMED,
        /** Nothing changed: the row is closing, or the confirm came too soon. / 无变化：该行正在关闭，或确认过快。 */
        IGNORED
    }

    /** Row presentation state. / 行的展示状态。 */
    public enum RowState {
        IDLE,
        ARMED,
        CLOSING
    }

    private int armedGate;
    private long armedAtTick;
    private long armedUntilTick;
    private final Map<Integer, Long> closingUntilTick = new HashMap<>();

    public Click click(int gate, long now) {
        if (state(gate, now) == RowState.CLOSING) {
            return Click.IGNORED;
        }
        if (state(gate, now) == RowState.ARMED) {
            if (now - armedAtTick < RiftGateConsoleClientRules.CONFIRM_MIN_DELAY_TICKS) {
                return Click.IGNORED;
            }
            armedGate = 0;
            closingUntilTick.put(gate, now + RiftGateConsoleClientRules.CLOSING_TIMEOUT_TICKS);
            return Click.CONFIRMED;
        }
        armedGate = gate;
        armedAtTick = now;
        armedUntilTick = now + RiftGateConsoleClientRules.CONFIRM_WINDOW_TICKS;
        return Click.ARMED;
    }

    public RowState state(int gate, long now) {
        Long closing = closingUntilTick.get(gate);
        if (closing != null && now < closing) {
            return RowState.CLOSING;
        }
        return gate != 0 && armedGate == gate && now < armedUntilTick ? RowState.ARMED : RowState.IDLE;
    }

    /** Drops expired states. / 清除过期状态。 */
    public void tick(long now) {
        if (armedGate != 0 && now >= armedUntilTick) {
            armedGate = 0;
        }
        closingUntilTick.values().removeIf(until -> now >= until);
    }

    /**
     * After a snapshot: forget every row whose gate is no longer listed (closed by us or anyone else).
     * 收到快照后：遗忘所有已不在列表中的门（无论是我们还是别人关的）。
     */
    public void retain(IntPredicate listed) {
        if (armedGate != 0 && !listed.test(armedGate)) {
            armedGate = 0;
        }
        for (Iterator<Integer> gates = closingUntilTick.keySet().iterator(); gates.hasNext(); ) {
            if (!listed.test(gates.next())) {
                gates.remove();
            }
        }
    }

    /** Disarms everything (e.g. the console lost its session). / 全部取消。 */
    public void clear() {
        armedGate = 0;
        closingUntilTick.clear();
    }
}
