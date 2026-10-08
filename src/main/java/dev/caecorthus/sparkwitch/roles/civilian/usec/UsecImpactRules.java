package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecBulletImpactsS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules for a shot's block impacts (D13, Q10): which blocks crack, how deep, and who is told. Every block the
 * bullet passed through before it stopped cracks, plus the block it stopped at (never the air, a player, a Seeker
 * device, a Magician puppet or an invisible map wall). The stage falls with the straight-line distance from the shooter's eye to the impact
 * point: up to 25 blocks is stage 9, then one stage less per further 25 blocks (or part of them), never below 2.
 * Cracks are visual only; no rule here (or anywhere in the fire path) changes a block.
 * 一次射击方块命中的纯规则（D13、Q10）：哪些方块出现裂痕、裂到几级、通知谁。子弹停下之前穿过的每个方块都会出现裂痕，
 * 外加它停下时撞上的方块（空中、玩家、搜寻者设备、魔术师皮套或隐形地图墙处都不会）。裂痕级别随射手眼睛到命中点的直线距离降低：
 * 25 格以内为 9 级，此后每再多 25 格（不足 25 格也算）降一级，最低 2 级。裂痕只是视觉效果；这里（以及整个开火路径）没有任何规则
 * 会改动方块。
 */
public final class UsecImpactRules {
    /** Distance up to which a crack is deepest. / 裂痕最深的距离上限。 */
    public static final double FULL_CRACK_DISTANCE = 25.0;
    /** Each further band of this many blocks costs one stage. / 每多出这么多格降一级。 */
    public static final double CRACK_STEP_DISTANCE = 25.0;
    public static final int MAX_CRACK_STAGE = 9;
    public static final int MIN_CRACK_STAGE = 2;
    /** Players within this distance of any impact receive the impacts packet. / 距任一命中点在此范围内的玩家会收到裂痕包。 */
    public static final double RECIPIENT_RADIUS = 64.0;
    /** Float slack when the stop distance is the traced path's own end. / 停止距离即路径终点时的浮点容差。 */
    static final double DISTANCE_TOLERANCE = 1.0E-6;

    private UsecImpactRules() {
    }

    /**
     * One cracked block: its position, the point the bullet met it (debris spawns there), the exit point for a
     * penetrated block (null for the stop block) and the crack stage.
     * 一个出现裂痕的方块：位置、子弹接触点（碎屑在此生成）、被穿透方块的出口点（停止方块为 null）以及裂痕级别。
     */
    public record Impact(BlockPos pos, Vec3d point, @Nullable Vec3d exit, int stage) {
        public Impact {
            pos = pos.toImmutable();
        }

        public UsecBulletImpactsS2CPacket.Impact toPacket() {
            return new UsecBulletImpactsS2CPacket.Impact(pos, stage);
        }
    }

    /** Crack stage for an impact this far from the shooter's eye. / 距射手眼睛这么远的命中点的裂痕级别。 */
    public static int crackStage(double distance) {
        if (!(distance > FULL_CRACK_DISTANCE)) {
            return MAX_CRACK_STAGE;
        }
        double bands = Math.ceil((distance - FULL_CRACK_DISTANCE) / CRACK_STEP_DISTANCE);
        if (!Double.isFinite(bands)) {
            return MIN_CRACK_STAGE;
        }
        return (int) Math.max(MIN_CRACK_STAGE, MAX_CRACK_STAGE - Math.min(bands, MAX_CRACK_STAGE));
    }

    /**
     * Impacts of a traced shot that ended at path distance {@code stopDistance}: the penetrated blocks entered strictly
     * before it, then the stop block only when the flight itself ended there (a player, device or puppet that stopped
     * the bullet earlier leaves the stop block untouched). Capped at {@link UsecBulletImpactsS2CPacket#MAX_IMPACTS}.
     * 在路径距离 {@code stopDistance} 处结束的一次射击的命中方块：在此之前（严格早于）进入的被穿透方块，以及仅当飞行本身止于
     * 停止方块时的该方块（更早拦下子弹的玩家、设备或皮套不会让停止方块开裂）。上限为 {@link UsecBulletImpactsS2CPacket#MAX_IMPACTS}。
     */
    public static List<Impact> impacts(Vec3d shooterEye, UsecTracer.Trace trace, double stopDistance) {
        List<Impact> impacts = new ArrayList<>();
        for (UsecTracer.Penetration penetration : trace.penetrations()) {
            if (impacts.size() >= UsecBulletImpactsS2CPacket.MAX_IMPACTS || !(penetration.distance() < stopDistance)) {
                break;
            }
            impacts.add(new Impact(penetration.pos(), penetration.entry(), penetration.exit(),
                    crackStage(shooterEye.distanceTo(penetration.entry()))));
        }
        UsecTracer.BlockStop stop = trace.blockStop();
        // Map walls are invisible (vanilla barrier, Wathe barrier panel): a crack or debris there would reveal them.
        // 地图墙是隐形的（原版屏障、Wathe 屏障板）：在其上出现裂痕或碎屑会暴露它们。
        if (stop != null && trace.stop() != UsecTracer.Stop.MAP_WALL
                && impacts.size() < UsecBulletImpactsS2CPacket.MAX_IMPACTS
                && !(stop.distance() > stopDistance + DISTANCE_TOLERANCE)) {
            impacts.add(new Impact(stop.pos(), stop.point(), null, crackStage(shooterEye.distanceTo(stop.point()))));
        }
        return List.copyOf(impacts);
    }

    /** Whether a player at {@code position} is told about these impacts. / 位于 {@code position} 的玩家是否收到这些裂痕。 */
    public static boolean isRecipient(Vec3d position, List<Impact> impacts) {
        double radiusSquared = RECIPIENT_RADIUS * RECIPIENT_RADIUS;
        for (Impact impact : impacts) {
            if (position.squaredDistanceTo(impact.point()) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }
}
