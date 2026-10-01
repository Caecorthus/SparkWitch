package dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchDeathRay;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Pure constants and geometry rules for Murderous Witch's Death Ray.
 * 杀意魔女“死亡射线”的纯常量与几何判断，方便测试且不扩大到其他职业。
 */
public final class MurderousWitchDeathRayRules {
    public static final Identifier DEATH_RAY_ID = SparkWitch.id("death_ray");
    public static final int COLOR = 0xC13838;
    public static final int MANA_COST = 100;
    public static final int WINDOW_TICKS = GameConstants.getInTicks(0, 10);
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);
    public static final int INITIAL_COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);
    public static final int MAX_CHARGES = 3;
    public static final double RANGE_BLOCKS = 12.0;
    public static final double PARTICLE_STEP_BLOCKS = 0.35;
    public static final float PARTICLE_SCALE = 1.2f;

    static final double TARGET_BOX_EXPANSION = 0.2;

    private MurderousWitchDeathRayRules() {
    }

    public static boolean canSelect(Role role) {
        return role == SparkWitchRoles.murderousWitch();
    }

    public static boolean isDeathRaySkill(Identifier skillId) {
        return DEATH_RAY_ID.equals(skillId);
    }

    public static boolean intersectsRay(Vec3d start, Vec3d direction, Box targetBox) {
        return intersectsRay(start, direction, targetBox, RANGE_BLOCKS);
    }

    public static boolean intersectsRay(Vec3d start, Vec3d direction, Box targetBox, double maxDistanceBlocks) {
        Vec3d normalizedDirection = normalize(direction);
        double normalizedMaxDistance = Math.max(0.0, Math.min(RANGE_BLOCKS, maxDistanceBlocks));
        if (normalizedDirection == Vec3d.ZERO || normalizedMaxDistance <= 0.0) {
            return false;
        }
        Vec3d end = start.add(normalizedDirection.multiply(normalizedMaxDistance));
        return targetBox.expand(TARGET_BOX_EXPANSION).raycast(start, end).isPresent();
    }

    /**
     * Hit test against lag-compensated volumes that the caller has already expanded; not expanded again here.
     * 针对调用方已扩展过的延迟补偿体积做命中判断；此处不再重复扩展。
     */
    public static boolean intersectsRay(Vec3d start, Vec3d direction, List<Box> volumes, double maxDistanceBlocks) {
        Vec3d normalizedDirection = normalize(direction);
        double normalizedMaxDistance = Math.max(0.0, Math.min(RANGE_BLOCKS, maxDistanceBlocks));
        if (normalizedDirection == Vec3d.ZERO || normalizedMaxDistance <= 0.0) {
            return false;
        }
        Vec3d end = start.add(normalizedDirection.multiply(normalizedMaxDistance));
        return HitscanLagRules.entryDistanceSquared(start, end, volumes) >= 0.0;
    }

    /**
     * Unit look vector for a client-reported yaw/pitch, same as {@code Entity#getRotationVector}; ZERO when either is
     * non-finite. / 客户端上报的偏航/俯仰对应的单位视线向量，与 {@code Entity#getRotationVector} 一致；任一非有限值时返回 ZERO。
     */
    public static Vec3d aimDirection(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return Vec3d.ZERO;
        }
        return normalize(Vec3d.fromPolar(MathHelper.clamp(pitch, -90.0F, 90.0F), MathHelper.wrapDegrees(yaw)));
    }

    static Vec3d normalize(Vec3d direction) {
        double lengthSquared = direction.lengthSquared();
        return lengthSquared <= 1.0E-7 ? Vec3d.ZERO : direction.multiply(1.0 / Math.sqrt(lengthSquared));
    }
}
