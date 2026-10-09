package dev.caecorthus.sparkwitch.client.usec;

/**
 * Pure client rules for the rifle's fire intent, recoil kick, the macOS Ctrl-click left press and the round reset of
 * the scope magnification. Presentation and intent only: the server re-checks every shot, so skipping a request here
 * only saves a packet.
 * 狙击步枪开火意图、后坐镜头抖动、macOS Ctrl 点击左键按下与瞄准镜倍率按局重置的纯客户端规则。仅为表现与意图：服务端
 * 复核每次开火，因此此处跳过请求只是省下一个数据包。
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

    /**
     * Whether a mouse event skips vanilla's macOS Ctrl-click remap ({@code Mouse.onMouseButton}: a left press with Ctrl
     * becomes a right press and is counted, and while that count is above zero the next left release becomes a right
     * release). Sprint defaults to Left Ctrl, so without this a Mac player sprinting with the AXMC scopes instead of
     * firing from the hip. True only for a left PRESS, no screen open, the rifle in the main hand, and no remapped
     * press still waiting for its release. A kept press is never counted, so its own release stays a left release;
     * releases are never kept, so a press remapped before the rifle was drawn still gets its remapped release and the
     * use key is never left down. Without Ctrl the skipped branch would change nothing for a press anyway.
     * 鼠标事件是否跳过原版 macOS Ctrl 点击重映射（{@code Mouse.onMouseButton}：按住 Ctrl 的左键按下变为右键按下并计数，
     * 计数大于零时下一次左键松开变为右键松开）。疾跑默认是左 Ctrl，否则 Mac 玩家疾跑中用 AXMC 腰射会变成开镜。仅当左键
     * 按下、未打开界面、主手持步枪且没有仍在等待松开的已重映射按下时为 true。保留的按下从不计数，因此它自己的松开仍是左键
     * 松开；松开从不保留，因此持枪前已重映射的按下仍会得到重映射的松开，使用键永不卡住。没有 Ctrl 时被跳过的分支对按下本就
     * 没有作用。
     *
     * @param pendingRemappedReleases vanilla's {@code controlLeftClicks} / 原版的 {@code controlLeftClicks}
     */
    public static boolean skipsMacControlClickRemap(boolean leftButton, boolean press, boolean screenOpen,
                                                    boolean rifleInMainHand, int pendingRemappedReleases) {
        return leftButton && press && !screenOpen && rifleInMainHand && pendingRemappedReleases == 0;
    }

    /**
     * Whether the remembered magnification goes back to 4x: a Wathe round begins when the client sees the game leave
     * INACTIVE (STARTING, or straight to ACTIVE) after an idle observation. The first observation of a connection
     * counts as idle, so joining or rejoining mid-round also starts at 4x; death, respawn and the round's own
     * STARTING -> ACTIVE -> STOPPING steps never reset it.
     * 记住的倍率是否回到 4 倍：客户端在空闲观察之后看到对局离开 INACTIVE（进入 STARTING 或直接 ACTIVE）即为一局开始。
     * 每次连接的第一次观察算作空闲，因此中途加入或重新加入也从 4 倍开始；死亡、重生以及对局自身的 STARTING -> ACTIVE ->
     * STOPPING 不会重置。
     *
     * @param wasIdle the previous observation had no round in progress / 上一次观察时没有进行中的对局
     * @param idle    no round in progress now (INACTIVE or no world) / 现在没有进行中的对局（INACTIVE 或没有世界）
     */
    public static boolean beginsRound(boolean wasIdle, boolean idle) {
        return wasIdle && !idle;
    }
}
