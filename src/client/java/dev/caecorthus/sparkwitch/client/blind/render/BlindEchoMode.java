package dev.caecorthus.sparkwitch.client.blind.render;

/**
 * Pure frame decision of the Blind's view (C13: fail closed). OFF releases everything; BLACK paints the world black
 * (shader pack, failed pipeline, missing depth capture, or the Blind does not see its own view: camera elsewhere, a
 * fake-death spectator, or swallowed, C7); ECHO runs the line-art pass. The unfiltered world is never an outcome while
 * the view is active.
 * 盲人视野的纯逐帧判定（C13：失败即全黑）。OFF 释放全部资源；BLACK 把世界画成全黑（光影包、管线失败、缺少深度捕获，
 * 或盲人看不到自己的视角：镜头在别处、假死旁观或被吞下，C7）；ECHO 运行线稿 pass。视野激活期间绝不会出现未过滤的世界画面。
 */
public enum BlindEchoMode {
    OFF,
    BLACK,
    ECHO;

    /** Frames without a depth capture before the HUD hint appears. / 连续缺少深度捕获多少帧后显示 HUD 提示。 */
    public static final int HINT_AFTER_MISSED_CAPTURES = 20;
    /** Rebuild backoff after a pipeline failure. / 管线失败后重建的退避时间。 */
    public static final int FIRST_RETRY_SECONDS = 2;
    public static final int MAX_RETRY_SECONDS = 30;

    /**
     * The Blind sees its own view: the camera is on itself, it is not a spectator (SparkTraits Last Stand pending or
     * Depression fake death; their camera falls back to the player when the body is gone) and not swallowed (C7).
     * 盲人能看到自己的视角：镜头在自己身上、不是旁观者（SparkTraits 最后一搏待定或抑郁假死；找不到身体时镜头会回到玩家
     * 自己身上）且未被吞下（C7）。
     */
    public static boolean seesOwnView(boolean cameraOnSelf, boolean spectator, boolean swallowed) {
        return cameraOnSelf && !spectator && !swallowed;
    }

    public static BlindEchoMode resolve(boolean viewActive, boolean ownView, boolean shaderPackInUse,
                                        boolean pipelineFailed, boolean depthCaptured) {
        if (!viewActive) {
            return OFF;
        }
        if (!ownView || shaderPackInUse || pipelineFailed || !depthCaptured) {
            return BLACK;
        }
        return ECHO;
    }

    /**
     * The HUD hint over the black view, never shown while merely swallowed or off-camera. Only a shader pack that
     * Iris reports asks the player to turn packs off; a failed pipeline or missing captures without one must not
     * blame shader packs.
     * 黑屏上的 HUD 提示，单纯被吞下或镜头不在自己身上时不显示。只有 Iris 报告的光影包才提示关闭光影包；
     * 没有光影包时的管线失败或缺少捕获不得归咎于光影包。
     */
    public static Hint hint(boolean viewActive, boolean shaderPackInUse, boolean pipelineFailed, int missedCaptures) {
        if (!viewActive) {
            return Hint.NONE;
        }
        if (shaderPackInUse) {
            return Hint.SHADER_PACK;
        }
        return pipelineFailed || missedCaptures >= HINT_AFTER_MISSED_CAPTURES ? Hint.RENDER_FAILED : Hint.NONE;
    }

    /**
     * Seconds the view stays black after its {@code failures}-th pipeline failure before it rebuilds: 2, 4, 8, 16,
     * then {@link #MAX_RETRY_SECONDS}. A one-off error recovers; a persistent one retries rarely.
     * 第 {@code failures} 次管线失败后保持全黑、再重建前等待的秒数：2、4、8、16，之后为 {@link #MAX_RETRY_SECONDS}。
     * 偶发错误可以恢复；持续错误只会偶尔重试。
     */
    public static int retryDelaySeconds(int failures) {
        int doublings = Math.min(Math.max(failures, 1) - 1, 5);
        return Math.min(FIRST_RETRY_SECONDS << doublings, MAX_RETRY_SECONDS);
    }

    public enum Hint {
        NONE,
        /** Iris reports a shader pack in use. / Iris 报告正在使用光影包。 */
        SHADER_PACK,
        /** The pipeline failed or the depth capture is missing, with no shader pack. / 无光影包时管线失败或缺少深度捕获。 */
        RENDER_FAILED
    }
}
