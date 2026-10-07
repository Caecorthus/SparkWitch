package dev.caecorthus.sparkwitch.client.usec;

import org.jetbrains.annotations.Nullable;

/**
 * Pure end-of-tick state machine for the local scope intent. A scope start (the rifle becomes in use) while sneaking
 * toggles the zoom level (S3: Shift + right-click), and every change of the raised-rifle state is reported once as a
 * {@code usec_scope} intent. A change seen while sending is impossible is kept and reported once sending works.
 * 本地开镜意图的纯刻末状态机。潜行时开始开镜（步枪进入使用状态）会切换倍率（S3：Shift + 右键）；举枪状态的每次变化
 * 都只作为一次 {@code usec_scope} 意图上报。无法发送时看到的变化会被保留，待可以发送时再上报一次。
 */
public final class UsecScopeTracker {
    private boolean wasUsing;
    private boolean reported;

    /**
     * One client tick end. Returns what to do: toggle the zoom, and the scoped value to send (null = nothing).
     * 一个客户端刻末尾。返回要做的事：是否切换倍率，以及要发送的开镜值（null 表示不发送）。
     */
    public Step tick(boolean usingRifle, boolean sneaking, boolean canSend) {
        boolean started = usingRifle && !wasUsing;
        wasUsing = usingRifle;
        Boolean send = null;
        if (canSend && usingRifle != reported) {
            reported = usingRifle;
            send = usingRifle;
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
