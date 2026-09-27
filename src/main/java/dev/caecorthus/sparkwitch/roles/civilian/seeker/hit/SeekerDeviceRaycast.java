package dev.caecorthus.sparkwitch.roles.civilian.seeker.hit;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen contract: nearest-wins device ray helpers (owner decision Q4). The nearest thing on the ray wins: a device
 * closer than the player target absorbs the hit and shields the player; a closer player is hit exactly as before.
 * Device boxes grow only by their small targeting margin; rays stop at COLLIDER blocks.
 * TODO(WP-04): implement. / 待 WP-04 实现。
 * 冻结契约：“最近者命中”的设备射线辅助方法（所有者决定 Q4）。射线上最近的物体命中：比玩家目标更近的设备吸收这次命中
 * 并挡住玩家；更近的玩家则与原来完全一样被命中。设备箱体只按很小的瞄准余量扩大；射线被 COLLIDER 方块阻挡。
 */
public final class SeekerDeviceRaycast {
    private SeekerDeviceRaycast() {
    }

    /**
     * Client target wrappers (revolver, derringer, knife, Demon Hunter): returns {@code original} unless a Seeker
     * device lies nearer on the look ray within {@code range}, then that device's EntityHitResult. Side-neutral.
     * 客户端目标包装（左轮、德林加、刀、猎魔枪）：除非视线上 {@code range} 内有更近的搜寻者设备，
     * 否则原样返回 {@code original}；有则返回该设备的 EntityHitResult。
     */
    public static HitResult preferNearerDevice(Entity shooter, HitResult original, double range) {
        return original;
    }

    /**
     * Ray check (side-neutral; server hooks and the optional CE taser crosshair hint): the nearest live device on
     * {@code start → end} that is nearer than {@code playerTarget} (or any device when the target is null); null when
     * the player (or nothing) is nearer.
     * 射线检查（两端通用；服务端钩子与可选的控场专家电击枪准星提示）：在 {@code start → end} 上比
     * {@code playerTarget} 更近（目标为空时任意）的最近存活设备；玩家更近或没有设备时返回 null。
     */
    @Nullable
    public static SeekerDeviceEntity blockingDevice(Entity shooter, Vec3d start, Vec3d end,
                                                    @Nullable Entity playerTarget) {
        return null;
    }

    /** Projectile sweep between two tick positions. / 投射物在两个刻位置之间的扫掠检测。 */
    @Nullable
    public static SeekerDeviceEntity projectileSweep(Entity projectile, Vec3d from, Vec3d to) {
        return null;
    }

    /** Pure nearest-wins comparison on squared distances. / 基于平方距离的纯“最近者命中”比较。 */
    public static boolean deviceWins(double deviceDistanceSquared, double targetDistanceSquared) {
        return deviceDistanceSquared < targetDistanceSquared;
    }
}
