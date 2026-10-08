package dev.caecorthus.sparkwitch.client.usec;

import org.jetbrains.annotations.Nullable;

/**
 * Pure end-of-tick state machine for the local scope intent. A raise start (the rifle becomes in use) while sneaking
 * toggles the zoom level (S3: Shift + right-click), whatever the view. Separately, every change of the actually scoped
 * state (A12b: the {@code client/scope} view gate holds and the USEC profile is the active one, so never in third
 * person or with a screen open) is reported once as a {@code usec_scope} intent, which drives the glint others see.
 * A change seen while sending is impossible is kept and reported once sending works.
 * 本地开镜意图的纯刻末状态机。潜行时开始举枪（步枪进入使用状态）会切换倍率（S3：Shift + 右键），与视角无关。另外，
 * 实际开镜状态的每次变化（A12b：{@code client/scope} 的视角条件成立且当前生效的是 USEC 配置，因此第三人称或打开界面时
 * 永远不算）都只作为一次 {@code usec_scope} 意图上报，它驱动他人看到的反光。无法发送时看到的变化会被保留，待可以发送时
 * 再上报一次。
 */
public final class UsecScopeTracker {
    private boolean wasUsing;
    private boolean reported;

    /**
     * One client tick end. Returns what to do: toggle the zoom, and the scoped value to send (null = nothing).
     * 一个客户端刻末尾。返回要做的事：是否切换倍率，以及要发送的开镜值（null 表示不发送）。
     *
     * @param usingRifle holding use with the rifle in the main hand (the raise) / 主手持步枪并按住使用键（举枪）
     * @param scoped     actually looking through the USEC scope this tick / 本刻确实通过 USEC 瞄准镜观察
     */
    public Step tick(boolean usingRifle, boolean scoped, boolean sneaking, boolean canSend) {
        boolean started = usingRifle && !wasUsing;
        wasUsing = usingRifle;
        Boolean send = null;
        if (canSend && scoped != reported) {
            reported = scoped;
            send = scoped;
        }
        return new Step(started && sneaking, send);
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

    /** One tick's outcome. / 单刻结果。 */
    public record Step(boolean toggleZoom, @Nullable Boolean send) {
    }
}
