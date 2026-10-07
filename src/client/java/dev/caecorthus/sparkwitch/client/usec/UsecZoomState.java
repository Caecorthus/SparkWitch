package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;

/**
 * The remembered zoom level of the local scope (S3). Client-only and never sent: the server does not care how far the
 * player zooms. It survives scope releases, rounds and reconnects for the whole game session, and starts at 4x.
 * 本地瞄准镜记住的倍率档位（S3）。仅客户端，从不发送：服务端不关心玩家放大多少。在整个游戏会话中跨越松开开镜、
 * 对局与重连保留，初始为 4 倍。
 */
public final class UsecZoomState {
    private static volatile int level;

    private UsecZoomState() {
    }

    public static int level() {
        return level;
    }

    /** FOV multiplier of the current level (0.25 or 0.125). / 当前档位的视野倍率（0.25 或 0.125）。 */
    public static float fovMultiplier() {
        return UsecRules.zoomFovMultiplier(level);
    }

    public static void toggle() {
        level = UsecInputRules.nextZoomLevel(level);
    }
}
