package dev.caecorthus.sparkwitch.client.sphinxhao;

/**
 * Pure client trim of the Sphinx Hao's two key presses (嘉豪病毒 on the ability key G, 豪气冲天 on the secondary key
 * N). The server owns every check; the synced owner-only ready ticks only drop presses that are certainly refused and
 * answer them with a local action-bar hint instead (no packet, no server line). A dead, spectating or swallowed Sphinx
 * Hao's press does nothing. No Minecraft client types, so it is unit-testable.
 * 狮身豪两次按键（技能键 G 的嘉豪病毒、第二技能键 N 的豪气冲天）的纯客户端过滤。全部校验在服务端；只同步给本人的就绪刻只用来
 * 丢弃必定被拒的按键，并改为本地动作栏提示（不发包，也没有服务端提示）。死亡、旁观或被吞下的狮身豪按键无效。不含 Minecraft 客户端
 * 类型，可做单元测试。
 */
public final class SphinxHaoKeyRules {
    private SphinxHaoKeyRules() {
    }

    /** What a press does. / 一次按键的结果。 */
    public enum Press {
        /** Nothing (not a living Sphinx Hao). / 无（不是存活的狮身豪）。 */
        IGNORE,
        /** Send the empty request; the server decides. / 发送空请求，由服务端决定。 */
        SEND,
        /** Local "cooling down N s" hint. / 本地“冷却 N 秒”提示。 */
        COOLDOWN_HINT,
        /**
         * Local "嘉豪病毒: unavailable during 豪气冲天" hint (嘉豪病毒 only; the server refuses it then).
         * 本地“嘉豪病毒：豪气冲天期间不可用”提示（仅嘉豪病毒；此时服务端会拒绝）。
         */
        DURING_ROAR_HINT,
        /** Local "陆泳中 N s" hint (豪气冲天 only). / 本地“陆泳中 N 秒”提示（仅豪气冲天）。 */
        ACTIVE_HINT
    }

    /**
     * 嘉豪病毒 (G), in the HUD row's order: a running 豪气冲天 window ({@code roarActive}), then the cooldown.
     * 嘉豪病毒（G），与 HUD 行顺序一致：先看进行中的豪气冲天窗口（{@code roarActive}），再看冷却。
     */
    public static Press infect(boolean livingSphinxHao, boolean roarActive, int cooldownTicks) {
        if (!livingSphinxHao) {
            return Press.IGNORE;
        }
        if (roarActive) {
            return Press.DURING_ROAR_HINT;
        }
        return cooldownTicks > 0 ? Press.COOLDOWN_HINT : Press.SEND;
    }

    /** 豪气冲天 (N): its own running window first, then the cooldown. / 豪气冲天（N）：先看自己进行中的窗口，再看冷却。 */
    public static Press roar(boolean livingSphinxHao, int crawlTicks, int cooldownTicks) {
        if (!livingSphinxHao) {
            return Press.IGNORE;
        }
        if (crawlTicks > 0) {
            return Press.ACTIVE_HINT;
        }
        return cooldownTicks > 0 ? Press.COOLDOWN_HINT : Press.SEND;
    }
}
