package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import org.jetbrains.annotations.Nullable;

/**
 * Frozen contract: movement delegate for {@code SeekerCarEntity}. Client side is logical only while the local owner
 * drives; server side is logical only while idle. The client driver installs itself through a hook so this common
 * class never references client code.
 * TODO(WP-09): implement idle physics and logical-side rules. / 待 WP-09 实现空闲物理与逻辑端规则。
 * 冻结契约：{@code SeekerCarEntity} 的移动委托。客户端仅在本地拥有者驾驶时为逻辑端；服务端仅在空闲时为逻辑端。
 * 客户端驾驶器通过钩子安装自身，因此该通用类从不引用客户端代码。
 */
public final class SeekerCarMovement {
    @Nullable
    private static volatile ClientDriveHook clientDriveHook;

    private SeekerCarMovement() {
    }

    /** Client-installed query: is this car driven by the local player? / 客户端安装的查询：该小车是否由本地玩家驾驶？ */
    @FunctionalInterface
    public interface ClientDriveHook {
        boolean isLocallyDriven(SeekerCarEntity car);
    }

    public static void installClientDriveHook(ClientDriveHook hook) {
        clientDriveHook = hook;
    }

    @Nullable
    public static ClientDriveHook clientDriveHook() {
        return clientDriveHook;
    }

    public static void serverIdleTick(SeekerCarEntity car) {
        // TODO(WP-09) / 待 WP-09 实现
    }

    public static boolean isLogicalSide(SeekerCarEntity car) {
        return !car.getWorld().isClient();
    }
}
