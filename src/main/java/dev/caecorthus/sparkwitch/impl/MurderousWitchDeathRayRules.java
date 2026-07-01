package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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
    public static final int MAX_CHARGES = 1;
    public static final double RANGE_BLOCKS = 8.0;
    public static final double PARTICLE_STEP_BLOCKS = 0.35;
    public static final float PARTICLE_SCALE = 1.2f;

    private static final double TARGET_BOX_EXPANSION = 0.2;

    private MurderousWitchDeathRayRules() {
    }

    public static boolean canSelect(Role role) {
        return role == SparkWitchRoles.murderousWitch();
    }

    public static boolean isDeathRaySkill(Identifier skillId) {
        return DEATH_RAY_ID.equals(skillId);
    }

    public static boolean intersectsRay(Vec3d start, Vec3d direction, Box targetBox) {
        Vec3d normalizedDirection = normalize(direction);
        if (normalizedDirection == Vec3d.ZERO) {
            return false;
        }
        Vec3d end = start.add(normalizedDirection.multiply(RANGE_BLOCKS));
        return targetBox.expand(TARGET_BOX_EXPANSION).raycast(start, end).isPresent();
    }

    static Vec3d normalize(Vec3d direction) {
        double lengthSquared = direction.lengthSquared();
        return lengthSquared <= 1.0E-7 ? Vec3d.ZERO : direction.multiply(1.0 / Math.sqrt(lengthSquared));
    }
}
