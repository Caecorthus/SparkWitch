package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;

/**
 * Pure client rules for the rifle's fire intent, recoil kick and zoom toggle. Presentation and intent only: the server
 * re-checks every shot, so skipping a request here only saves a packet.
 * 狙击步枪开火意图、后坐镜头抖动与倍率切换的纯客户端规则。仅为表现与意图：服务端复核每次开火，因此此处跳过请求
 * 只是省下一个数据包。
 */
public final class UsecInputRules {
    private UsecInputRules() {
    }

    /**
     * Whether a press edge the latch let through becomes a fire request. A refused press is still swallowed, so the
     * rifle never attacks, mines or swings. Skipping during the synced rifle cooldown (bolt or round-start lock) only
     * saves a packet; the server re-checks every condition.
     * 闩锁放行的按下沿是否变成发射请求。被拒绝的按键仍会被吞掉，因此步枪永远不会攻击、挖掘或挥动。在已同步的步枪冷却
     * （拉栓或开局锁定）期间跳过只是为了省一个数据包；服务端会复核所有条件。
     */
    public static boolean sendsFire(boolean screenOpen, boolean spectator, boolean cameraIsPlayer,
                                    boolean coolingDown, boolean canSend) {
        return !screenOpen && !spectator && cameraIsPlayer && !coolingDown && canSend;
    }

    /**
     * The local recoil kick plays only for a sent request with a round in the synced chamber, so an empty click never
     * looks like a shot. Purely visual: the aim already went out with the request.
     * 只有已发出的请求且同步弹膛中有子弹时才播放本地后坐抖动，空膛点击不会看起来像开火。纯视觉：朝向已随请求发出。
     */
    public static boolean kicksOnFire(boolean sent, boolean chamberLoaded) {
        return sent && chamberLoaded;
    }

    /** Clamps a remembered zoom level into the table. / 将记住的倍率档位钳制到表内。 */
    public static int clampZoomLevel(int level) {
        return Math.max(0, Math.min(UsecRules.ZOOM_FOV_MULTIPLIERS.length - 1, level));
    }

    /** Shift + right-click cycles 4x -> 8x -> 4x. / Shift + 右键循环切换 4 倍 -> 8 倍 -> 4 倍。 */
    public static int nextZoomLevel(int level) {
        return (clampZoomLevel(level) + 1) % UsecRules.ZOOM_FOV_MULTIPLIERS.length;
    }

    /** Magnification shown to the player (4 or 8). / 展示给玩家的放大倍数（4 或 8）。 */
    public static int magnification(int level) {
        return Math.round(1.0F / UsecRules.zoomFovMultiplier(level));
    }
}
