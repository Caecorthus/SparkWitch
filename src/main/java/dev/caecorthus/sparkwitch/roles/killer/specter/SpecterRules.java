package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.Optional;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Specter (幽魂) tuning and role predicate. Step 1 is a plain Wathe killer clone: only the name and colour differ.
 * 幽魂的数值与职业判定。第一步是纯 Wathe 杀手克隆：只有名称与颜色不同。
 */
public final class SpecterRules {
    public static final Identifier ROLE_ID = SparkWitch.id("specter");
    // Dried blood: dark red with reduced saturation. / 干涸血迹：低饱和暗红。
    public static final int COLOR = 0x6B2F2F;
    // Owner-provided outline colours: 25.png is #485FAD, 50.png is #05AC99.
    // 所有者提供的描边颜色：25.png 为 #485FAD，50.png 为 #05AC99。
    public static final float LOW_SANITY_THRESHOLD = 25.0F;
    public static final float MID_SANITY_THRESHOLD = 50.0F;
    public static final int LOW_SANITY_COLOR = 0x485FAD;
    public static final int MID_SANITY_COLOR = 0x05AC99;
    public static final int OUTLINE_PRIORITY = 300;
    // Low-sanity players see the invisible Specter in the role colour; outrank generic faction outlines.
    // 低理智玩家看到的隐身幽魂使用职业色描边，优先级高于通用阵营描边。
    public static final int LOW_SANITY_SPECTER_OUTLINE_PRIORITY = 900;
    // Cowardice: blue-purple outline in the Specter's view, between the mark and the sanity bands.
    // 怯懦：幽魂视角的蓝紫色描边，优先级在标记与理智区间之间。
    public static final int COWARDICE_COLOR = 0x6A5ACD;
    public static final int COWARDICE_OUTLINE_PRIORITY = 500;
    // The marked target's outline uses the role colour and outranks the sanity bands.
    // 被标记者描边使用职业色，优先级高于理智区间。
    public static final int MARK_OUTLINE_PRIORITY = 1_000;
    public static final double MARK_RANGE = 3.0D;
    // Owner decision: 10 s mark, 45 s initial cooldown, 45 s cooldown after the shriek; an expired mark costs nothing.
    // 所有者决定：标记 10 秒，开局冷却 45 秒，尖啸后冷却 45 秒；标记自然过期不进入冷却。
    public static final int MARK_DURATION_TICKS = 10 * 20;
    public static final int INITIAL_COOLDOWN_TICKS = 45 * 20;
    public static final int SHRIEK_COOLDOWN_TICKS = 45 * 20;
    public static final Identifier MARK_SKILL_ID = SparkWitch.id("specter_mark");
    public static final Identifier SHRIEK_SKILL_ID = SparkWitch.id("specter_shriek");
    public static final Identifier OTHERWORLD_SKILL_ID = SparkWitch.id("specter_otherworld_skill");
    // Fear: up to 3 levels; level 1/2 drain every 10 s, level 3 every 5 s; one level decays every 60 s; sanity floor 5.
    // 恐惧：最多 3 层；1/2 层每 10 秒扣 san，3 层每 5 秒；每 60 秒掉 1 层；理智最低扣到 5。
    public static final int FEAR_MAX_LEVEL = 3;
    // Ghost trigger: -30 sanity, then cowardice for non-killer fake-mood roles and the Savior.
    // 鬼魂触发器：扣 30 理智；非杀手队的虚假理智职业与救世主获得怯懦。
    public static final float TRIGGER_SANITY_DRAIN = 30.0F;
    public static final int FEAR_DECAY_TICKS = 60 * 20;
    public static final float FEAR_MIN_SANITY = 5.0F;
    public static final double FEAR_SHRIEK_RADIUS = 5.0D;
    // Skill two (Otherworld): unlock 120 s after role assignment, 30 s active, 120 s cooldown counted from the end.
    // 技能二（里世界）：分配职业后 120 秒解锁，持续 30 秒，冷却 120 秒从结束时开始算。
    public static final int SECOND_SKILL_UNLOCK_TICKS = 120 * 20;
    public static final int SECOND_SKILL_DURATION_TICKS = 30 * 20;
    public static final int SECOND_SKILL_COOLDOWN_TICKS = 120 * 20;
    // Speed II equivalent as a movement-speed attribute modifier (+40%), removed when the window ends.
    // 速度 II 用移速属性修饰符实现（+40%），窗口结束时移除。
    public static final Identifier SECOND_SKILL_SPEED_MODIFIER_ID = SparkWitch.id("specter_otherworld_speed");
    public static final double SECOND_SKILL_SPEED_BONUS = 0.40D;
    // Opening shields: 3 layers per alive real-mood player below 25 sanity. Shields clear with the window.
    // 开启护盾：每个存活的真实理智低于 25 的玩家提供 3 层，窗口结束时清空。
    public static final int SECOND_SKILL_SHIELD_PER_LOW_SANITY = 3;
    // HUD colours blend 1:1 to COLOR: (0xA72F2F + 0x2F2F2F) / 2 = 0x6B2F2F.
    // HUD 颜色按 1:1 平均正好是职业色 0x6B2F2F。
    public static final int PRIMARY_HUD_COLOR = 0xA72F2F;
    public static final int SECONDARY_HUD_COLOR = 0x2F2F2F;

    private SpecterRules() {
    }

    public static boolean isSpecter(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /** Ticks between two sanity drains at the given fear level. / 指定恐惧层数的扣 san 间隔（tick）。 */
    public static int fearDrainIntervalTicks(int level) {
        return level >= FEAR_MAX_LEVEL ? 5 * 20 : 10 * 20;
    }

    /** Sanity lost per drain at the given fear level. / 指定恐惧层数每次扣除的 san。 */
    public static float fearDrainSanity(int level) {
        return switch (level) {
            case 1 -> 5.0F;
            case 2 -> 10.0F;
            default -> level >= FEAR_MAX_LEVEL ? 20.0F : 0.0F;
        };
    }

    /** Sanity band colour: {@code <=25} uses the 25.png colour, {@code 25 < san <= 50} the 50.png colour.
     *  理智区间颜色：{@code <=25} 用 25.png 的颜色，{@code 25 < san <= 50} 用 50.png 的颜色。 */
    public static @Nullable Integer sanityHighlightColor(float sanityPercent) {
        if (sanityPercent <= LOW_SANITY_THRESHOLD) {
            return LOW_SANITY_COLOR;
        }
        if (sanityPercent <= MID_SANITY_THRESHOLD) {
            return MID_SANITY_COLOR;
        }
        return null;
    }

    /**
     * Nearest player hit by the crosshair ray within {@link #MARK_RANGE} blocks, requiring line of sight.
     * 准星射线 {@link #MARK_RANGE} 格内最近的玩家，并要求视线可见。
     */
    public static @Nullable PlayerEntity findMarkTarget(PlayerEntity caster) {
        Vec3d start = caster.getEyePos();
        Vec3d end = start.add(caster.getRotationVec(1.0F).multiply(MARK_RANGE));
        PlayerEntity nearest = null;
        double nearestSquared = MARK_RANGE * MARK_RANGE;
        for (PlayerEntity target : caster.getWorld().getPlayers()) {
            if (target == caster
                    || target.isSpectator()
                    || !GameFunctions.isPlayerPlayingAndAlive(target)
                    || !caster.canSee(target)) {
                continue;
            }
            Optional<Vec3d> hit = target.getBoundingBox()
                    .expand(target.getTargetingMargin())
                    .raycast(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double hitSquared = hit.get().squaredDistanceTo(start);
            if (hitSquared <= nearestSquared) {
                nearestSquared = hitSquared;
                nearest = target;
            }
        }
        return nearest;
    }
}
