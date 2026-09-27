package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Client remote-view controller on START_CLIENT_TICK: calls only MinecraftClient#setCameraEntity; restores the camera only while it is still our focus.
 * TODO(WP-10a): implement. / 待 WP-10a 实现。
 * START_CLIENT_TICK 上的客户端遥控视角控制器：只调用 MinecraftClient#setCameraEntity；仅当相机仍是我们的焦点时才恢复。
 */
public final class SeekerRemoteViewClient {
    private SeekerRemoteViewClient() {
    }

    public static void register() {
        // TODO / 待实现
    }

    public static boolean isActive() {
        return false;
    }

    public static SeekerSessionMode mode() {
        return SeekerSessionMode.NONE;
    }

    @Nullable
    public static Entity focus() {
        return null;
    }

    /** Disconnect, join, respawn, world change. / 断线、加入、重生、切换世界。 */
    public static void forceExit() {
    }

    public static boolean isDriving(Entity entity) {
        return false;
    }

    /** @return true when the look input was consumed by the remote focus. / 视角输入被遥控焦点消费时返回 true。 */
    public static boolean onLook(double deltaX, double deltaY) {
        return false;
    }
}
