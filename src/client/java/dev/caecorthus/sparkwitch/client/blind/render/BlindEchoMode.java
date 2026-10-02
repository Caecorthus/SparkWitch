package dev.caecorthus.sparkwitch.client.blind.render;

/**
 * Pure frame decision of the Blind's view (C13: fail closed). OFF releases everything; BLACK paints the world black
 * (shader pack, failed pipeline, missing depth capture, or the Blind does not see its own view: camera elsewhere or
 * swallowed, C7); ECHO runs the line-art pass. The unfiltered world is never an outcome while the view is active.
 * 盲人视野的纯逐帧判定（C13：失败即全黑）。OFF 释放全部资源；BLACK 把世界画成全黑（光影包、管线失败、缺少深度捕获，
 * 或盲人看不到自己的视角：镜头在别处或被吞下，C7）；ECHO 运行线稿 pass。视野激活期间绝不会出现未过滤的世界画面。
 */
public enum BlindEchoMode {
    OFF,
    BLACK,
    ECHO;

    /** Frames without a depth capture before the HUD hint appears. / 连续缺少深度捕获多少帧后显示 HUD 提示。 */
    public static final int HINT_AFTER_MISSED_CAPTURES = 20;

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
     * The "turn shader packs off" hint: shown only for problems the player can fix, never while merely swallowed.
     * “请关闭光影包”提示：只在玩家能自行解决的问题时显示，单纯被吞下时不显示。
     */
    public static boolean showsHint(boolean viewActive, boolean shaderPackInUse, boolean pipelineFailed,
                                    int missedCaptures) {
        return viewActive && (shaderPackInUse || pipelineFailed || missedCaptures >= HINT_AFTER_MISSED_CAPTURES);
    }
}
