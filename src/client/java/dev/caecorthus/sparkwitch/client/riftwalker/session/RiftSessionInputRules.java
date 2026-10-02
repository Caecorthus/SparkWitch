package dev.caecorthus.sparkwitch.client.riftwalker.session;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.net.RiftHopC2SPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Pure client input rules while inside a Rift Gate (plan §6.4–6.5, D11): the key allowlist, fresh-press edges, the
 * ⬅️/➡️ slot model (scroll, keys 1/2, use key, A/D), the vanilla-equivalent scroll accumulator, send spacing and the
 * per-tick session edge decision. No client instance is touched, so every rule is unit-tested without a game. The
 * client only predicts presentation and sends requests; the server re-validates every hop and exit.
 * 在裂隙门内时的纯客户端输入规则（plan §6.4–6.5、D11）：按键白名单、新按下沿、⬅️/➡️ 两格模型（滚轮、1/2 键、使用键、
 * A/D）、与原版等价的滚轮累加器、发送间隔，以及每刻的会话边沿判定。这里不触碰客户端实例，因此每条规则都可以脱离游戏
 * 做单元测试。客户端只负责表现与发送请求；每次跳门与出门都由服务端重新校验。
 */
public final class RiftSessionInputRules {
    /** Simple Voice Chat's key category (push-to-talk, whisper, mute). / Simple Voice Chat 的按键分类。 */
    public static final String VOICE_CHAT_CATEGORY = "key.categories.voicechat";
    /**
     * Wathe's instinct key: kept so the coloured instinct outlines (D9/D10) still work inside the grey view.
     * Wathe 的本能键：保留，使门内灰色画面中仍能看到带颜色的本能描边（D9/D10）。
     */
    public static final String WATHE_INSTINCT_KEY = "key.wathe.instinct";
    /**
     * Keys that keep their normal state inside a gate: sneak (exit), left/right strafe (hop), player list, screenshot,
     * fullscreen and instinct. Everything else (attack, use, pick, hotbar, inventory, drop, jump, sprint, perspective,
     * chat, spectator outlines, skills) reads as released for vanilla and other mods; the controller reads the keys it
     * needs raw.
     * 门内保持正常状态的按键：潜行（出门）、左右平移（跳门）、玩家列表、截图、全屏与本能。其余按键（攻击、使用、选取、
     * 快捷栏、背包、丢弃、跳跃、疾跑、视角、聊天、旁观者描边、技能）对原版与其他模组都视为未按下；控制器自行读取所需按键的原始状态。
     */
    public static final Set<String> ALLOWED_KEYS = Set.of(
            "key.sneak",
            "key.left",
            "key.right",
            "key.playerlist",
            "key.screenshot",
            "key.fullscreen",
            WATHE_INSTINCT_KEY);
    /** Slot 0: ⬅️ previous gate. / 第 0 格：⬅️ 上一扇门。 */
    public static final int PREVIOUS_SLOT = 0;
    /** Slot 1: ➡️ next gate. / 第 1 格：➡️ 下一扇门。 */
    public static final int NEXT_SLOT = 1;
    public static final int SLOT_COUNT = 2;
    /** Selection at every session start. / 每次进门时的默认选中格。 */
    public static final int DEFAULT_SLOT = NEXT_SLOT;
    /**
     * Client spacing of hop requests: the server throttle (D11, 0.5 s) plus a two-tick margin for jitter, so a granted
     * press is never silently dropped by the server.
     * 跳门请求的客户端间隔：服务端节流（D11，0.5 秒）加两刻抖动余量，避免已发出的请求被服务端静默丢弃。
     */
    public static final int HOP_SEND_SPACING_TICKS = RiftwalkerRules.HOP_THROTTLE_TICKS + 2;
    /** Spacing of repeated exit requests while Shift keeps being pressed. / 持续按 Shift 时重复出门请求的间隔。 */
    public static final int EXIT_RESEND_TICKS = 10;
    /** "Never sent" marker for the spacing clocks. / 间隔计时的「从未发送」标记。 */
    public static final long NEVER = Long.MIN_VALUE / 4;

    private RiftSessionInputRules() {
    }

    /** True when the key keeps its normal state inside a gate. / 门内该按键保持正常状态时返回 true。 */
    public static boolean isAllowedKey(@Nullable String translationKey, @Nullable String category) {
        return VOICE_CHAT_CATEGORY.equals(category) || (translationKey != null && ALLOWED_KEYS.contains(translationKey));
    }

    /**
     * A press counts only on a fresh key-down: released on the previous tick and down now or queued since (a tap
     * shorter than a tick). OS key repeat queues presses only while the key stays down, so a held key fires once. The
     * controller treats every key as down at the entry edge, so a key held while entering must be released first.
     * 只认新的按下沿：上一刻为松开，且现在按下或其间积压了按下（短于一刻的轻点）。系统按键重复只在按住期间积压，因此按住只触发一次。
     * 控制器在进门边沿把所有按键视为按下，因此进门时一直按住的键必须先松开。
     */
    public static boolean freshPress(boolean wasDown, boolean down, boolean queued) {
        return !wasDown && (down || queued);
    }

    /**
     * Shift exit is armed once Shift is seen released after the entry edge (queued presses are drained there), so the
     * crouch used to walk up to a gate never exits at once. While armed, every queued press asks to leave.
     * 进门边沿之后看到 Shift 松开一次才会「上膛」（进门时已清空积压按键），因此走近门时的下蹲不会立刻出门。上膛后每次积压的按下都请求出门。
     */
    public static boolean exitRequested(boolean armed, int queuedPresses) {
        return armed && queuedPresses > 0;
    }

    /** Arming state after this tick. / 本刻之后的上膛状态。 */
    public static boolean armAfter(boolean armed, boolean down) {
        return armed || !down;
    }

    /** Hop direction of a slot: ⬅️ previous (−1), ➡️ next (+1). / 格子对应的跳门方向。 */
    public static byte direction(int slot) {
        return slot == PREVIOUS_SLOT ? RiftHopC2SPacket.PREVIOUS : RiftHopC2SPacket.NEXT;
    }

    /** Slot that shows a direction. / 某方向对应的格子。 */
    public static int slotFor(int direction) {
        return direction < 0 ? PREVIOUS_SLOT : NEXT_SLOT;
    }

    /**
     * One hop per tick at most: a fresh A/D press fires ⬅️/➡️ directly (both at once cancel out and also swallow the use
     * key this tick); otherwise a fresh use-key press fires the selected slot. 0 = no hop.
     * 每刻最多跳一次：新按下 A/D 直接触发 ⬅️/➡️（同时按下互相抵消，且本刻也忽略使用键）；否则新按下使用键触发选中格。0 表示不跳。
     */
    public static int hopDirection(boolean previousFresh, boolean nextFresh, boolean useFresh, int selectedSlot) {
        if (previousFresh || nextFresh) {
            return previousFresh == nextFresh ? 0 : previousFresh ? RiftHopC2SPacket.PREVIOUS : RiftHopC2SPacket.NEXT;
        }
        return useFresh ? direction(selectedSlot) : 0;
    }

    /**
     * Keys 1/2 select ⬅️/➡️ (2 wins when both are queued in one tick); other selections are kept.
     * 1/2 键选择 ⬅️/➡️（同一刻都积压时 2 优先）；否则保持当前选择。
     */
    public static int hotbarSelection(int selectedSlot, boolean firstQueued, boolean secondQueued) {
        if (secondQueued) {
            return NEXT_SLOT;
        }
        return firstQueued ? PREVIOUS_SLOT : normalizeSlot(selectedSlot);
    }

    /**
     * Scrolling moves the selection like vanilla {@code scrollInHotbar} (up = left) and wraps around the two slots.
     * 滚轮与原版 {@code scrollInHotbar} 一样移动选择（向上为左），在两格之间循环。
     */
    public static int scrollSlot(int selectedSlot, int steps) {
        return Math.floorMod(normalizeSlot(selectedSlot) - steps, SLOT_COUNT);
    }

    public static int normalizeSlot(int slot) {
        return Math.floorMod(slot, SLOT_COUNT);
    }

    /** Spacing clock: true when {@code spacing} ticks passed since {@code last} (or never sent). / 间隔是否已过。 */
    public static boolean elapsed(long now, long last, int spacing) {
        return last == NEVER || now - last >= spacing;
    }

    /**
     * Per-tick edge decision against the owner-synced component. The client never predicts entry or exit: START only
     * when the server says inside, END when it no longer does, when the local player object was replaced (respawn,
     * join) or when a different session id appears (exit and re-entry inside one tick → RESTART).
     * 每刻相对于仅同步给拥有者的组件的边沿判定。客户端从不预测进门或出门：仅当服务端报告在门内时 START；不再在门内、本地玩家
     * 对象被替换（重生、加入）或出现不同的会话 id 时 END（一刻内出门又进门 → RESTART）。
     */
    public static Transition transition(boolean active, boolean playerChanged, boolean inside, boolean sessionChanged) {
        if (!active) {
            return inside ? Transition.START : Transition.NONE;
        }
        if (!inside) {
            return Transition.END;
        }
        return playerChanged || sessionChanged ? Transition.RESTART : Transition.NONE;
    }

    /**
     * Vanilla {@code Mouse#onMouseScroll} accumulation (discrete scroll, wheel sensitivity, sign reset, truncation;
     * vertical wins, horizontal is negated), kept in our own accumulator because the vanilla one is never reached
     * while inside.
     * 与原版 {@code Mouse#onMouseScroll} 一致的累加（离散滚动、滚轮灵敏度、反向清零、截断；优先纵向，横向取反），使用我们自己的累加器，
     * 因为门内永远不会走到原版累加器。
     */
    public static Scroll accumulate(double accumulatedHorizontal, double accumulatedVertical, double horizontal,
                                    double vertical, boolean discrete, double sensitivity) {
        double deltaHorizontal = (discrete ? Math.signum(horizontal) : horizontal) * sensitivity;
        double deltaVertical = (discrete ? Math.signum(vertical) : vertical) * sensitivity;
        double nextHorizontal = accumulatedHorizontal;
        double nextVertical = accumulatedVertical;
        if (nextHorizontal != 0.0 && Math.signum(deltaHorizontal) != Math.signum(nextHorizontal)) {
            nextHorizontal = 0.0;
        }
        if (nextVertical != 0.0 && Math.signum(deltaVertical) != Math.signum(nextVertical)) {
            nextVertical = 0.0;
        }
        nextHorizontal += deltaHorizontal;
        nextVertical += deltaVertical;
        int wholeHorizontal = (int) nextHorizontal;
        int wholeVertical = (int) nextVertical;
        if (wholeHorizontal == 0 && wholeVertical == 0) {
            return new Scroll(nextHorizontal, nextVertical, 0);
        }
        nextHorizontal -= wholeHorizontal;
        nextVertical -= wholeVertical;
        return new Scroll(nextHorizontal, nextVertical, wholeVertical == 0 ? -wholeHorizontal : wholeVertical);
    }

    /** Scroll accumulator state and the whole steps to apply now. / 滚轮累加器状态与本次应用的整步数。 */
    public record Scroll(double horizontal, double vertical, int steps) {
    }

    /** Local reaction to one tick of synced state. / 对单刻同步状态的本地反应。 */
    public enum Transition {
        NONE,
        /** The server put the local player inside a gate. / 服务端让本地玩家进入了门。 */
        START,
        /** The server ended the session (any reason). / 服务端结束了会话（任意原因）。 */
        END,
        /** New player object or a new session id: end the old session, then start again. / 新玩家对象或新会话 id：先结束再开始。 */
        RESTART
    }
}
