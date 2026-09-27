package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Frozen contract: pure car physics shared by the owner-client simulation and the server replay, using the 0.4x0.6
 * traversal box rather than the entity hit box.
 * TODO(WP-09): implement collision, step-up and gravity. / 待 WP-09 实现碰撞、台阶与重力。
 * 冻结契约：拥有者客户端模拟与服务端重放共用的纯小车物理，使用 0.4×0.6 通行箱而非实体命中箱。
 */
public final class SeekerCarPhysics {
    private SeekerCarPhysics() {
    }

    public record Result(Vec3d position, Vec3d velocity, boolean onGround, boolean horizontalCollision) {
    }

    /** Traversal box centred horizontally on {@code position}, feet at its y. / 以位置为水平中心、脚底为 y 的通行箱。 */
    public static Box traversalBox(Vec3d position) {
        double half = SeekerRules.CAR_TRAVERSAL_WIDTH / 2.0;
        return new Box(position.x - half, position.y, position.z - half,
                position.x + half, position.y + SeekerRules.CAR_TRAVERSAL_HEIGHT, position.z + half);
    }

    /** Velocity from raw input axes and yaw. / 由原始输入轴与朝向得到的速度。 */
    public static Vec3d inputVelocity(float forward, float sideways, float yaw, Vec3d previousVelocity,
                                      boolean onGround) {
        return Vec3d.ZERO;
    }

    public static Result move(World world, Vec3d position, Vec3d velocity) {
        return new Result(position, Vec3d.ZERO, true, false);
    }
}
