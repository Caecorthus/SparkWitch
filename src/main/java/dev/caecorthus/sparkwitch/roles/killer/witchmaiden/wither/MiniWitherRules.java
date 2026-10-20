package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Stable ids, tuning and pure rules of the Witch Maiden's second skill, the Mini Wither (owner 2026-10-20): unlocked by
 * two completed tasks together with the Wither Gohei, toggled on key 2, auto-firing wither skulls every 5 s, and firing
 * a lethal crimson skull from the Gohei for 75 mana.
 * 巫女第二技能「小凋灵」（所有者 2026-10-20）的稳定标识、数值与纯规则：完成两个任务后连同凋灵御币一起解锁，按技能键 2
 * 召唤或收回，每 5 秒自动射出凋灵之首，手持凋灵御币左键花费 75 魔力射出致命的赤色凋灵之首。
 */
public final class MiniWitherRules {
    public static final Identifier SKILL_ID = SparkWitch.id("mini_wither");
    public static final Identifier CRIMSON_SKULL_ID = SparkWitch.id("crimson_wither_skull");
    public static final Identifier SKULL_ENTITY_ID = SparkWitch.id("mini_wither_skull");
    public static final Identifier GOHEI_ID = SparkWitch.id("wither_gohei");
    /** SparkFactionAPI action asked before a skull or burst touches a player. / 头颅或爆炸触及玩家前询问的 SFA 行为 id。 */
    public static final Identifier AFFECT_ACTION = SKILL_ID;

    public static final int UNLOCK_TASKS = 2;
    public static final int TOGGLE_COOLDOWN_TICKS = GameConstants.getInTicks(0, 10);
    public static final int AUTO_FIRE_INTERVAL_TICKS = GameConstants.getInTicks(0, 5);
    public static final double TARGET_RANGE = 24.0;
    public static final double SKULL_SPEED = 1.0;
    public static final int SKULL_LIFETIME_TICKS = GameConstants.getInTicks(0, 3);
    public static final double CRIMSON_SPEED = 4.0;
    public static final double CRIMSON_RANGE = 48.0;
    public static final int CRIMSON_LIFETIME_TICKS = (int) Math.ceil(CRIMSON_RANGE / CRIMSON_SPEED);
    public static final int CRIMSON_COOLDOWN_TICKS = GameConstants.getInTicks(0, 15);
    public static final int CRIMSON_MANA_COST = 75;
    /** Half the burst cube's edge: the 2x2x2 burst reaches 1 block from its centre. / 2x2x2 爆炸立方体的半边长。 */
    public static final double BURST_HALF_SIZE = 1.0;
    public static final int BURST_EFFECT_TICKS = GameConstants.getInTicks(0, 2);
    public static final int SLOWNESS_AMPLIFIER = 1;
    public static final int SPEED_AMPLIFIER = 0;

    public static final int AMBIENT_MIN_INTERVAL_TICKS = 80;
    public static final int AMBIENT_MAX_INTERVAL_TICKS = 140;
    public static final float AMBIENT_VOLUME = 0.25F;
    public static final float AMBIENT_PITCH = 1.7F;

    /** Hover point in the feet frame (+X left, +Y up, +Z facing). / 脚下坐标系中的悬浮点（+X 向左、+Y 向上、+Z 朝向）。 */
    public static final double ANCHOR_LATERAL = 0.6;
    public static final double ANCHOR_ABOVE_EYE = 0.15;
    public static final double ANCHOR_BACK = 0.55;
    public static final double BOB_AMPLITUDE = 0.06;
    public static final double BOB_SPEED = 0.12;
    /** The hover point stops this far short of a wall between the eye and it. / 视线到悬浮点被墙挡住时离墙的距离。 */
    public static final double WALL_MARGIN = 0.35;
    /**
     * Skulls start behind her head, so their first stretch passes her own camera: no trail there, and her own first
     * person skips the model. / 头颅从她脑后出发，起始一段会掠过她自己的镜头：这一段不留尾迹，她自己的第一人称也不绘制模型。
     */
    public static final double OWNER_VIEW_CLEARANCE = 2.0;

    private MiniWitherRules() {
    }

    /** Whether {@code point} is too close to the owner's eye to draw. / {@code point} 是否离主人眼睛太近而不应绘制。 */
    public static boolean withinOwnerClearance(Vec3d eye, Vec3d point) {
        return eye.squaredDistanceTo(point) < OWNER_VIEW_CLEARANCE * OWNER_VIEW_CLEARANCE;
    }

    /** True only on the task that reaches the threshold. / 只有达到阈值的那个任务返回 true。 */
    public static boolean unlocksOn(int before, int after) {
        return before < UNLOCK_TASKS && after >= UNLOCK_TASKS;
    }

    public static boolean isUnlocked(int completedTasks) {
        return completedTasks >= UNLOCK_TASKS;
    }

    /**
     * Whom the Mini Wither works against. A Witch Maiden whose effective faction is no longer the killers (SparkTraits
     * Conscience) fires at anyone and her bursts grant Speed (owner Q9); otherwise she fires at non-killers.
     * 小凋灵针对的对象。有效阵营不再是杀手的巫女（SparkTraits 善良）向任何人射击，爆炸给予速度（所有者 Q9）；否则只打非杀手。
     */
    public enum Mode {
        HOSTILE,
        CONSCIENCE
    }

    /** Unknown faction stays HOSTILE. / 阵营未知时保持 HOSTILE。 */
    public static Mode mode(@Nullable Identifier ownerEffectiveFaction) {
        return ownerEffectiveFaction != null && !FactionIds.KILLER.equals(ownerEffectiveFaction)
                ? Mode.CONSCIENCE : Mode.HOSTILE;
    }

    /**
     * The killer team for HOSTILE mode: an effective killer (the Impostor trait included, owner 2026-10-20) or the
     * NoellesRoles Undercover, whom killers see as a teammate. An unknown faction fails closed as a teammate.
     * HOSTILE 模式下的杀手队伍：有效阵营为杀手（含内鬼词条，所有者 2026-10-20），或被杀手视作队友的 NoellesRoles 卧底。
     * 阵营未知时按队友处理（失败关闭）。
     */
    public static boolean isKillerTeam(@Nullable Identifier effectiveFaction, boolean undercover) {
        return undercover || effectiveFaction == null || FactionIds.KILLER.equals(effectiveFaction);
    }

    /** Auto-fire target test; the owner is never a target. / 自动射击目标判定；本人永远不是目标。 */
    public static boolean isTarget(Mode mode, boolean self, boolean killerTeam) {
        if (self) {
            return false;
        }
        return mode == Mode.CONSCIENCE || !killerTeam;
    }

    /** Players an auto-fired skull flies through. / 自动射出的凋灵之首会穿过的玩家。 */
    public static boolean skullPassesThrough(Mode mode, boolean self, boolean killerTeam) {
        return self || mode == Mode.HOSTILE && killerTeam;
    }

    /** Players a burst applies its effect to; Speed reaches the owner too. / 爆炸施加效果的玩家；速度也会给本人。 */
    public static boolean burstAffects(Mode mode, boolean self, boolean killerTeam) {
        return mode == Mode.CONSCIENCE || !self && !killerTeam;
    }

    /**
     * The crimson skull stops at anyone but its owner (owner Q4: friendly fire).
     * 赤色凋灵之首会被除本人外的任何人挡下（所有者 Q4：会误伤）。
     */
    public static boolean crimsonPassesThrough(boolean self) {
        return self;
    }

    /** Whether the 2x2x2 burst cube touches {@code box}. / 2x2x2 爆炸立方体是否触及该箱体。 */
    public static boolean inBurst(Vec3d center, Box box) {
        return box.intersects(center.subtract(BURST_HALF_SIZE, BURST_HALF_SIZE, BURST_HALF_SIZE),
                center.add(BURST_HALF_SIZE, BURST_HALF_SIZE, BURST_HALF_SIZE));
    }

    /**
     * Hover point in the feet frame: behind the main-hand shoulder (owner rule), above eye height, with a slow bob.
     * 脚下坐标系中的悬浮点：主手一侧肩膀后方（所有者规则），高于眼睛，并缓慢上下浮动。
     */
    public static Vec3d localAnchor(boolean rightSide, double eyeHeight, float age) {
        double bob = BOB_AMPLITUDE * MathHelper.sin(age * (float) BOB_SPEED);
        return new Vec3d(rightSide ? -ANCHOR_LATERAL : ANCHOR_LATERAL, eyeHeight + ANCHOR_ABOVE_EYE + bob,
                -ANCHOR_BACK);
    }

    /**
     * Rotates a feet-frame offset by the body yaw into world axes (+Z facing at yaw 0, +X left).
     * 把脚下坐标系的偏移按身体偏航转到世界坐标轴（偏航为 0 时 +Z 为朝向、+X 为左）。
     */
    public static Vec3d toWorldOffset(Vec3d local, float bodyYaw) {
        float radians = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        float sin = MathHelper.sin(radians);
        float cos = MathHelper.cos(radians);
        return new Vec3d(local.x * cos - local.z * sin, local.y, local.x * sin + local.z * cos);
    }

    /**
     * Fraction of the eye-to-anchor segment the Wither may use when a wall cuts it at {@code hitDistance}; 1 when clear.
     * 视线到悬浮点的线段在 {@code hitDistance} 处被墙截断时小凋灵可用的比例；无遮挡时为 1。
     */
    public static double clampFraction(double segmentLength, double hitDistance) {
        if (!(segmentLength > 0.0) || !Double.isFinite(hitDistance) || hitDistance >= segmentLength) {
            return 1.0;
        }
        return MathHelper.clamp((hitDistance - WALL_MARGIN) / segmentLength, 0.0, 1.0);
    }

    /**
     * Yaw in degrees (Minecraft convention: 0 faces +Z) of a direction; the heads turn to the last shot (owner rule).
     * 方向的偏航角（Minecraft 约定：0 朝 +Z）；三个头转向上一次射击的方向（所有者规则）。
     */
    public static float yawOf(Vec3d direction) {
        return (float) (MathHelper.atan2(-direction.x, direction.z) * MathHelper.DEGREES_PER_RADIAN);
    }

    /** Pitch in degrees of a direction (positive looks down). / 方向的俯仰角（正值向下）。 */
    public static float pitchOf(Vec3d direction) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return (float) (MathHelper.atan2(-direction.y, horizontal) * MathHelper.DEGREES_PER_RADIAN);
    }

    /** Angle in radians between the look vector and the direction to a target. / 视线与目标方向之间的夹角（弧度）。 */
    public static double angleTo(Vec3d look, Vec3d toTarget) {
        double lengths = look.length() * toTarget.length();
        if (!(lengths > 0.0)) {
            return Math.PI;
        }
        return Math.acos(MathHelper.clamp(look.dotProduct(toTarget) / lengths, -1.0, 1.0));
    }

    /** Ticks until the next ambient call. / 距离下一次环境叫声的刻数。 */
    public static int ambientDelay(double random) {
        double clamped = MathHelper.clamp(random, 0.0, 1.0);
        return AMBIENT_MIN_INTERVAL_TICKS
                + (int) Math.floor(clamped * (AMBIENT_MAX_INTERVAL_TICKS - AMBIENT_MIN_INTERVAL_TICKS));
    }
}
