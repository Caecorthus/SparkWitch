package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerBreakSource;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteRules;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen contract: movement delegate for {@code SeekerCarEntity}. Client side is logical only while the local owner
 * drives; server side is logical only while idle. The client driver installs itself through a hook so this common
 * class never references client code (dedicated servers never load the hook's implementation).
 * Authority: while driven, the owner's client simulates the car and the server validates each move
 * ({@code SeekerCarMoveService}); while idle, the server alone applies gravity here and breaks the car when it falls
 * below the play area.
 * 冻结契约：{@code SeekerCarEntity} 的移动委托。客户端仅在本地拥有者驾驶时为逻辑端；服务端仅在空闲时为逻辑端。
 * 客户端驾驶器通过钩子安装自身，因此该通用类从不引用客户端代码（专用服务端永远不会加载钩子实现）。
 * 权威划分：驾驶中由拥有者客户端模拟、服务端逐包校验（{@code SeekerCarMoveService}）；空闲时仅由服务端在此施加重力，
 * 并在小车跌出游戏区域下方时将其损坏。
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

    /**
     * Server tick while nobody drives the car: gravity/drag through {@link SeekerCarPhysics} (no input), then the VOID
     * check ({@code y < playArea.minY} breaks the car with {@link SeekerBreakSource#VOID}). A no-op while driven, on
     * the client, or for a removed car.
     * 无人驾驶时的服务端刻：经 {@link SeekerCarPhysics} 施加重力与阻力（无输入），随后做虚空检查（低于游戏区域最低点即以
     * {@link SeekerBreakSource#VOID} 损坏）。驾驶中、客户端或已移除的小车为空操作。
     */
    public static void serverIdleTick(SeekerCarEntity car) {
        if (car == null || car.getWorld().isClient() || car.isRemoved() || isServerDriven(car)) {
            return;
        }
        Vec3d position = car.getPos();
        SeekerCarPhysics.Result result = SeekerCarPhysics.move(car.getWorld(), position,
                SeekerCarPhysics.inputVelocity(0.0F, 0.0F, car.getYaw(), car.getVelocity(), car.isOnGround()));
        if (!result.position().equals(position)) {
            car.setPosition(result.position());
        }
        car.setVelocity(result.velocity());
        car.setOnGround(result.onGround());
        breakIfInVoid(car);
    }

    /**
     * Breaks the car (source VOID, no breaker) when its feet are below the Wathe play area. Returns true when broken.
     * 小车脚底低于 Wathe 游戏区域时将其损坏（来源 VOID，无损坏者）。已损坏返回 true。
     */
    public static boolean breakIfInVoid(SeekerCarEntity car) {
        if (car.isRemoved()
                || !SeekerRemoteRules.isBelowPlayArea(MapVariablesWorldComponent.KEY.get(car.getWorld()).getPlayArea(),
                car.getY())) {
            return false;
        }
        SeekerDeviceService.breakDevice(car, SeekerBreakSource.VOID, null);
        return true;
    }

    /**
     * Client: logical only while the installed hook reports local driving. Server: logical only while idle.
     * 客户端：仅当已安装的钩子报告本地驾驶时为逻辑端。服务端：仅空闲时为逻辑端。
     */
    public static boolean isLogicalSide(SeekerCarEntity car) {
        if (car.getWorld().isClient()) {
            ClientDriveHook hook = clientDriveHook;
            return hook != null && hook.isLocallyDriven(car);
        }
        return !isServerDriven(car);
    }

    /**
     * Server only: the owner currently holds a CAR session focused on this exact car entity.
     * 仅服务端：拥有者当前正以 CAR 会话聚焦于这台小车实体。
     */
    public static boolean isServerDriven(SeekerCarEntity car) {
        if (car.getWorld().isClient()) {
            return false;
        }
        ServerPlayerEntity owner = SeekerDeviceService.findOwner(car);
        if (owner == null) {
            return false;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(owner);
        return status != null && status.sessionMode() == SeekerSessionMode.CAR && status.carEntityId() == car.getId();
    }
}
