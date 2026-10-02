package dev.caecorthus.sparkwitch.client.blind.gate;

/**
 * Pure presentation gates for the local Blind (D2, D4, C5, C6). Only OTHER players are gated: the local player is never
 * hidden, and non-player entities (corpses, items, Seeker devices) are environment (C6) and render normally. A
 * perceived player renders even when invisible (D4); an unperceived one never renders at all.
 * 本地盲人的纯展示闸门（D2、D4、C5、C6）。只对其他玩家生效：本地玩家永不隐藏，非玩家实体（尸体、掉落物、搜寻者装置）
 * 属于环境（C6），照常渲染。被感知的玩家即使隐身也会渲染（D4）；未被感知的玩家完全不渲染。
 */
public final class BlindGateRules {
    private BlindGateRules() {
    }

    /** An unperceived other player is not drawn into the Blind's frame. / 未被感知的其他玩家不进入盲人的画面。 */
    public static boolean hidesEntity(boolean viewActive, boolean otherPlayer, boolean perceived) {
        return viewActive && otherPlayer && !perceived;
    }

    /**
     * D4: a perceived other player draws its body even when invisible; never a spectator (only a floating head would
     * draw) and never an active Wraith (D4: not perceivable).
     * D4：被感知的其他玩家即使隐身也画出身体；旁观者（只会画出漂浮的头）与活跃冤魂（D4：不可感知）除外。
     */
    public static boolean forcesVisible(boolean viewActive, boolean otherPlayer, boolean perceived, boolean spectator,
                                        boolean activeWraith) {
        return viewActive && otherPlayer && perceived && !spectator && !activeWraith;
    }

    /**
     * Held items, armor, capes and mod features never draw on other players, so line art shows no weapon shape.
     * 其他玩家身上的手持物、护甲、披风与模组附加层一律不画，线稿因此不会露出武器轮廓。
     */
    public static boolean suppressesFeatures(boolean viewActive, boolean otherPlayer) {
        return viewActive && otherPlayer;
    }
}
