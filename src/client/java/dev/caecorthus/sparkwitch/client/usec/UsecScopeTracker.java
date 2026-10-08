package dev.caecorthus.sparkwitch.client.usec;

import org.jetbrains.annotations.Nullable;

/**
 * Pure state machine for the local scope intent. A raise start (the rifle becomes in use) while sneaking jumps the
 * magnification to the farther end of 1x-6x (Shift + right-click), whatever the view; {@link #afterInput} reports it
 * right after vanilla's input handling, before the first zoom sample of that tick. Separately, every change of the
 * actually scoped state (A12b: the {@code client/scope} view gate holds and the USEC profile is the active one, so
 * never in third person or with a screen open) is reported once by {@link #endTick} as a {@code usec_scope} intent,
 * which drives the glint others see. A change seen while sending is impossible is kept and reported once sending works.
 * Both calls follow the raised state, so a raise is seen exactly once even on ticks where vanilla skips input handling
 * (a screen is open).
 * 本地开镜意图的纯状态机。潜行时开始举枪（步枪进入使用状态）会把倍率跳到 1-6 倍中较远的一端（Shift + 右键），与视角无关；
 * {@link #afterInput} 在原版输入处理之后、该刻第一次倍率采样之前报告它。另外，实际开镜状态的每次变化（A12b：
 * {@code client/scope} 的视角条件成立且当前生效的是 USEC 配置，因此第三人称或打开界面时永远不算）都由 {@link #endTick}
 * 只作为一次 {@code usec_scope} 意图上报，它驱动他人看到的反光。无法发送时看到的变化会被保留，待可以发送时再上报一次。
 * 两个调用都会跟踪举枪状态，因此即使某刻原版跳过输入处理（打开了界面），一次举枪也只会被看到一次。
 */
public final class UsecScopeTracker {
    private boolean wasUsing;
    private boolean reported;

    /**
     * Right after vanilla's input handling. True when the rifle was raised this tick while sneaking: jump the zoom.
     * 紧接在原版输入处理之后。本刻在潜行时举起步枪则返回 true：跳转倍率。
     *
     * @param usingRifle holding use with the rifle in the main hand (the raise) / 主手持步枪并按住使用键（举枪）
     */
    public boolean afterInput(boolean usingRifle, boolean sneaking) {
        boolean started = usingRifle && !wasUsing;
        wasUsing = usingRifle;
        return started && sneaking;
    }

    /**
     * One client tick end. Returns the scoped value to send, or null for nothing.
     * 一个客户端刻末尾。返回要发送的开镜值，null 表示不发送。
     *
     * @param usingRifle holding use with the rifle in the main hand (the raise) / 主手持步枪并按住使用键（举枪）
     * @param scoped     actually looking through the USEC scope this tick / 本刻确实通过 USEC 瞄准镜观察
     */
    public @Nullable Boolean endTick(boolean usingRifle, boolean scoped, boolean canSend) {
        wasUsing = usingRifle;
        if (canSend && scoped != reported) {
            reported = scoped;
            return scoped;
        }
        return null;
    }

    /**
     * Disconnect or a non-SparkWitch server: forget everything; the server's flag is per connection and its tick sweep
     * clears a stale one.
     * 断线或非 SparkWitch 服务器：全部遗忘；服务端标记按连接保存，其逐刻清理会清除过期标记。
     */
    public void reset() {
        wasUsing = false;
        reported = false;
    }
}
