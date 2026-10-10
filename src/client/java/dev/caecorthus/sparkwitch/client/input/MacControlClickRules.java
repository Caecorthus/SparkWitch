package dev.caecorthus.sparkwitch.client.input;

/**
 * Neutral pure rule for vanilla's macOS Ctrl-click remap, shared by the left-click weapons that answer the single
 * {@code MinecraftClient.IS_SYSTEM_MAC} read in {@code Mouse.onMouseButton} (Potion Gunner launcher, Murderous Witch
 * Death Ray). Vanilla 1.21.1 turns a left press with Ctrl into a right press and counts it in
 * {@code controlLeftClicks}; while that count is above zero the next left release becomes a right release and the count
 * drops. Sprint defaults to Left Ctrl, so a Mac player sprinting would use the held item instead of firing. Each
 * caller owns its mixin and decides only whether its own weapon owns the left click; this class holds no state and no
 * Minecraft types. The AXMC keeps its own identical copy in {@code client/usec/UsecInputRules} (2026-10-09).
 * 原版 macOS Ctrl 点击重映射的中立纯规则，供应答 {@code Mouse.onMouseButton} 中唯一一次 {@code MinecraftClient.IS_SYSTEM_MAC}
 * 读取的左键武器共用（药炮手炮筒、杀意魔女死亡射线）。原版 1.21.1 把按住 Ctrl 的左键按下变为右键按下，并计入
 * {@code controlLeftClicks}；计数大于零时下一次左键松开变为右键松开并减少计数。疾跑默认是左 Ctrl，因此 Mac 玩家疾跑时会
 * 使用手持物品而不是开火。每个调用方拥有自己的 mixin，只判断自己的武器是否占有左键；本类无状态，也不含 Minecraft 类型。
 * AXMC 在 {@code client/usec/UsecInputRules} 中保留一份相同的规则（2026-10-09）。
 */
public final class MacControlClickRules {
    private MacControlClickRules() {
    }

    /**
     * Whether a mouse event keeps vanilla's Ctrl-click branch from running: true only for a left PRESS, no screen open,
     * a weapon that owns the left click, and no remapped press still waiting for its release. A kept press is never
     * counted, so its own release stays a left release; releases are never kept, so a press remapped before the weapon
     * owned the click still gets its remapped release and the use key is never left down. Without Ctrl the skipped
     * branch would change nothing for a press anyway.
     * 鼠标事件是否让原版 Ctrl 点击分支不执行：仅当左键按下、未打开界面、武器占有左键且没有仍在等待松开的已重映射按下时为
     * true。保留的按下从不计数，因此它自己的松开仍是左键松开；松开从不保留，因此武器占有左键之前已重映射的按下仍会得到
     * 重映射的松开，使用键永不卡住。没有 Ctrl 时被跳过的分支对按下本就没有作用。
     *
     * @param ownsLeftClick           the caller's weapon would take this left press / 调用方的武器会接管这次左键按下
     * @param pendingRemappedReleases vanilla's {@code controlLeftClicks} / 原版的 {@code controlLeftClicks}
     */
    public static boolean keepsLeftPress(boolean leftButton, boolean press, boolean screenOpen, boolean ownsLeftClick,
                                         int pendingRemappedReleases) {
        return leftButton && press && !screenOpen && ownsLeftClick && pendingRemappedReleases == 0;
    }
}
