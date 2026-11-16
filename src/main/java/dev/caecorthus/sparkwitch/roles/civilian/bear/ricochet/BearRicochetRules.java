package dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet;

import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.VisorCrack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Pure Rys-T ricochet rules (owner H1-H6, coordinator defaults D1-D5, 2026-11-05), side-neutral and free of world
 * state so every branch is unit-tested. The server hook ({@link BearRicochetService}) feeds it the victim's head yaw,
 * the resolved shot origin and the visor state; the client daze reads only the timing constants.
 * <ul>
 *   <li>D2 ranged set: hitscan, projectiles, rays, the TR shell and every explosion; never melee, poison, curses,
 *   falls, {@code gun_shot_backfire} or {@code shot_innocent}. {@link #BLAST_REASONS} never borrow the shooter's eye
 *   as the origin (the thrower is not where the blast is).</li>
 *   <li>D3 front: the horizontal 180° hemisphere around the wearer's head yaw (SparkStrength
 *   {@code BodyguardRules.isInFront} precedent); an unknown origin or one inside the head's footprint is "not front".</li>
 *   <li>H3: visor DOWN rolls from every direction; visor UP never ricochets a shot from the front.</li>
 *   <li>D4: a glass hit (crack) iff the visor is down and the shot came from the front.</li>
 * </ul>
 * 纯 Rys-T 跳弹规则（所有者 H1-H6，协调者默认 D1-D5，2026-11-05），两端通用、不读世界状态，便于逐条单测。服务端钩子
 * （{@link BearRicochetService}）传入受害者头部朝向、解析出的射击来源与面罩状态；客户端眩晕只读取时长常量。D2 远程集合：
 * 命中扫描、投射物、射线、TR 炮弹与所有爆炸；从不包括近战、中毒、诅咒、坠落、{@code gun_shot_backfire} 与
 * {@code shot_innocent}。{@link #BLAST_REASONS} 从不借用射手眼睛作为来源（投掷者不在爆点）。D3 正面：以佩戴者头部朝向为中心的
 * 水平 180° 半球（SparkStrength {@code BodyguardRules.isInFront} 先例）；来源未知或落在头部投影内视为“非正面”。H3：面罩放下时
 * 任意方向都掷骰；面罩抬起时正面来的射击永不跳弹。D4：面罩放下且来自正面时才命中玻璃（留下裂纹）。
 */
public final class BearRicochetRules {
    // ---- D2 ranged set (literal ids so the rules load without the providers) ----
    public static final Identifier GUN_SHOT = Identifier.of("wathe", "gun_shot");
    public static final Identifier GRENADE = Identifier.of("wathe", "grenade");
    public static final Identifier DEMON_HUNTER_SHOT = Identifier.of("noellesroles", "demon_hunter_shot");
    public static final Identifier THROWING_AXE = Identifier.of("noellesroles", "throwing_axe");
    public static final Identifier BOMB = Identifier.of("noellesroles", "bomb");
    public static final Identifier PIERCED_BY_RAY = Identifier.of("sparkwitch", "pierced_by_ray");
    public static final Identifier NINJA_SHURIKEN_KILL = Identifier.of("sparkwitch", "ninja_shuriken_kill");
    public static final Identifier CRIMSON_WITHER_SKULL = Identifier.of("sparkwitch", "crimson_wither_skull");
    public static final Identifier POTION_SHELL = Identifier.of("sparkwitch", "potion_shell");
    public static final Identifier POTION_BACKBLAST = Identifier.of("sparkwitch", "potion_backblast");
    /**
     * Savior pistol (PR #200): a gunshot for the Rys-T. Its up-to-10 same-tick kill retries are one call burst of one
     * shooter ({@link AttackSource}), so they share one roll (H2) and a deflection holds for every retry.
     * 救世主手枪（PR #200）：对 Rys-T 而言是一次枪击。它在同一刻内最多 10 次的击杀重试属于同一射手的同一调用批次
     * （{@link AttackSource}），因此共用一次掷骰（H2），一次弹开对每次重试都有效。
     */
    public static final Identifier SAVIOR_PISTOL_KILL = Identifier.of("sparkwitch", "savior_pistol_kill");

    /** Every death reason the Rys-T may deflect (D2). / Rys-T 可能弹开的所有死因（D2）。 */
    public static final Set<Identifier> RANGED_REASONS = Set.of(GUN_SHOT, GRENADE, DEMON_HUNTER_SHOT, THROWING_AXE,
            BOMB, PIERCED_BY_RAY, NINJA_SHURIKEN_KILL, CRIMSON_WITHER_SKULL, POTION_SHELL, POTION_BACKBLAST,
            SAVIOR_PISTOL_KILL);
    /**
     * Blasts centred away from the killer: without an explicit origin scope their origin is unknown, never the killer's
     * eye. The potion backblast counts as an explosion for the ranged set (owner H1) but is NOT listed: it leaves the
     * gunner's eyes, so the killer's eye is its exact origin. / 爆心不在击杀者处的爆炸：没有显式来源作用域时来源未知，绝不取
     * 击杀者眼睛。药炮尾焰在远程集合中算作爆炸（所有者 H1），但不在此列：它从药炮手眼睛喷出，因此击杀者眼睛就是其确切来源。
     */
    public static final Set<Identifier> BLAST_REASONS = Set.of(GRENADE, BOMB, POTION_SHELL);

    // ---- D3 / D4 geometry ----
    /**
     * An origin closer than this (blocks, horizontally) to the wearer's axis is "at the victim" (NR bomb, a grenade at
     * the feet): not front. / 水平距佩戴者轴线小于此值（格）的来源视为“在受害者处”（NR 炸弹、脚下手雷）：非正面。
     */
    public static final double MIN_FRONT_OFFSET = 0.3;
    /** Half the glass's horizontal / vertical field, in degrees, for the crack mapping. / 裂纹映射用的玻璃半视场（度）。 */
    public static final float GLASS_HALF_YAW_DEGREES = 90.0F;
    public static final float GLASS_HALF_PITCH_DEGREES = 60.0F;
    /** Random spread added to a crack's u/v so repeated hits do not stack. / 裂纹 u/v 的随机散布，避免重复命中叠在一处。 */
    public static final float GLASS_JITTER = 0.08F;
    /** Crack sprite variants rolled; WP2 wraps any value. / 随机的裂纹贴图变体数；WP2 会回绕任意值。 */
    public static final int CRACK_STYLES = 4;

    // ---- D5 presentation (server sound range, client daze timing) ----
    /** Public ping volume: vanilla range 16·volume ≈ 24 blocks. / 公开声音音量：原版范围 16·音量 ≈ 24 格。 */
    public static final float PUBLIC_SOUND_VOLUME = 1.5F;
    /** View jolt + double vision, ~1.5 s. / 视角震动 + 重影，约 1.5 秒。 */
    public static final int DAZE_TICKS = 30;
    /** Tinnitus B with world sounds muffled under it, ~2 s. / 耳鸣 B 及其下的世界声音发闷，约 2 秒。 */
    public static final int MUFFLE_TICKS = 40;
    /** World volume while muffled (0..1). / 发闷期间的世界音量（0..1）。 */
    public static final float MUFFLE_VOLUME = 0.3F;
    /** Tinnitus volume for the wearer only. / 仅佩戴者听到的耳鸣音量。 */
    public static final float TINNITUS_VOLUME = 0.8F;

    // ---- Smoke carving radii (S3 + bullet tunnels) ----
    /** Hitscan tunnel radius. / 命中扫描通道半径。 */
    public static final float TUNNEL_RADIUS = 0.35F;
    /** Potion backblast lane radius. / 药炮尾焰通道半径。 */
    public static final float BACKBLAST_TUNNEL_RADIUS = 0.5F;
    public static final float GRENADE_HOLE_RADIUS = 3.0F;
    public static final float FIRECRACKER_HOLE_RADIUS = 1.5F;
    public static final float BOMB_HOLE_RADIUS = 3.0F;
    /** NR bomb centre: the carrier's feet + this. / NR 炸弹中心：持有者脚下 + 此值。 */
    public static final double BOMB_CENTRE_LIFT = 0.5;
    /** Holy Flash: a stun grenade, not a blast; a small hole only (its own radius is 6). / 圣光弹：震撼弹而非爆炸，只开小洞（其自身半径为 6）。 */
    public static final float HOLY_FLASH_HOLE_RADIUS = 3.0F;
    public static final float MINI_WITHER_HOLE_RADIUS = 1.0F;
    /** Wathe revolver family client pick range (RevolverItem.getGunTarget). / Wathe 左轮系客户端选取距离。 */
    public static final double REVOLVER_RANGE = 30.0;
    /** NoellesRoles Demon Hunter pistol client pick range. / NoellesRoles 猎魔枪客户端选取距离。 */
    public static final double DEMON_HUNTER_RANGE = 5.0;

    private BearRicochetRules() {
    }

    public static boolean isRanged(@Nullable Identifier reason) {
        return reason != null && RANGED_REASONS.contains(reason);
    }

    /**
     * Whether a scope-less kill may use the killer's eye as its origin (direct fire), D3 step 3.
     * 没有作用域的击杀能否以击杀者眼睛为来源（直射），D3 第 3 步。
     */
    public static boolean usesShooterOrigin(@Nullable Identifier reason) {
        return isRanged(reason) && !BLAST_REASONS.contains(reason);
    }

    /**
     * D3 origin order: an open SparkWitch {@code RangedOriginScope} decides alone (its unknown scope stays unknown);
     * else, for a blast, SparkStrength's blast scope; else, for direct fire, the killer's eye; else unknown (null).
     * {@code sparkStrengthBlast} is asked only for blasts. Sources whose origin IS the killer's eye at kill time push no
     * scope: the revolver family, the Hunter shotgun, the USEC rifle, the Death Ray, the Demon Hunter and the backblast.
     * D3 来源顺序：打开的 SparkWitch {@code RangedOriginScope} 单独决定（其未知作用域保持未知）；否则爆炸类取 SparkStrength 的
     * 爆炸作用域；否则直射类取击杀者眼睛；否则未知（null）。只有爆炸类才询问 {@code sparkStrengthBlast}。击杀时来源就是击杀者
     * 眼睛的来源不压作用域：左轮系、猎人霰弹枪、USEC 步枪、死亡射线、猎魔枪与尾焰。
     */
    public static @Nullable Vec3d resolveOrigin(Identifier reason, boolean scopeOpen, @Nullable Vec3d scoped,
                                                Supplier<Vec3d> sparkStrengthBlast,
                                                @Nullable Vec3d killerEye) {
        if (scopeOpen) {
            return scoped;
        }
        if (reason != null && BLAST_REASONS.contains(reason)) {
            return sparkStrengthBlast.get();
        }
        return usesShooterOrigin(reason) ? killerEye : null;
    }

    /**
     * D3: the origin lies in the horizontal hemisphere the wearer's head faces. Null, non-finite or "at the victim"
     * (closer than {@link #MIN_FRONT_OFFSET}) is not front. / D3：来源位于佩戴者头部朝向的水平半球内。null、非有限或
     * “在受害者处”（近于 {@link #MIN_FRONT_OFFSET}）不算正面。
     */
    public static boolean isFront(float headYawDegrees, double wearerX, double wearerZ, @Nullable Vec3d origin) {
        if (origin == null || !Double.isFinite(origin.x) || !Double.isFinite(origin.z) || !Float.isFinite(headYawDegrees)) {
            return false;
        }
        double towardOriginX = origin.x - wearerX;
        double towardOriginZ = origin.z - wearerZ;
        double length = Math.sqrt(towardOriginX * towardOriginX + towardOriginZ * towardOriginZ);
        if (length < MIN_FRONT_OFFSET) {
            return false;
        }
        double yaw = Math.toRadians(headYawDegrees);
        return towardOriginX * -Math.sin(yaw) + towardOriginZ * Math.cos(yaw) > 0.0;
    }

    /** H3: whether the shot gets the 25% roll at all. / H3：这一枪是否参与 25% 掷骰。 */
    public static boolean rolls(boolean visorDown, boolean front) {
        return visorDown || !front;
    }

    /** D4: a ricochet off the visor glass (crack) rather than the helmet shell. / D4：弹开于面罩玻璃（裂纹）而非头盔外壳。 */
    public static boolean hitsGlass(boolean visorDown, boolean front) {
        return visorDown && front;
    }

    /**
     * H2/H3: one roll ({@code roll} in [0, 1)) against {@link BearRules#RICOCHET_CHANCE}; the roll is not drawn when
     * the shot cannot ricochet. / H2/H3：一次掷骰（{@code roll} 取 [0, 1)）对比 {@link BearRules#RICOCHET_CHANCE}；不可能跳弹时
     * 不抽取随机数。
     */
    public static boolean ricochets(boolean visorDown, boolean front, FloatRoll roll) {
        return rolls(visorDown, front) && roll.next() < BearRules.RICOCHET_CHANCE;
    }

    /**
     * The crack a glass hit leaves (placeholder mapping until WP2's {@code RysTVisorGeometry.glassPoint}): the yaw of
     * the origin relative to the head picks u (right of the wearer = larger u), its elevation picks v (above = smaller
     * v), plus a small random spread, a random angle and style. Unknown origin hits the centre.
     * 玻璃命中留下的裂纹（WP2 的 {@code RysTVisorGeometry.glassPoint} 到来前的占位映射）：来源相对头部的偏航决定 u（佩戴者右侧
     * u 更大），仰角决定 v（上方 v 更小），再加少量随机散布、随机角度与样式。来源未知时命中中心。
     */
    public static VisorCrack glassCrack(float headYawDegrees, Vec3d eye, @Nullable Vec3d origin, FloatRoll roll) {
        float u = 0.5F;
        float v = 0.5F;
        if (origin != null && eye != null) {
            Vec3d toward = origin.subtract(eye);
            double horizontal = Math.sqrt(toward.x * toward.x + toward.z * toward.z);
            if (horizontal > 1.0E-6 || Math.abs(toward.y) > 1.0E-6) {
                float originYaw = (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
                float relativeYaw = MathHelper.wrapDegrees(originYaw - headYawDegrees);
                float elevation = (float) Math.toDegrees(Math.atan2(toward.y, horizontal));
                u = 0.5F + 0.5F * MathHelper.clamp(relativeYaw / GLASS_HALF_YAW_DEGREES, -1.0F, 1.0F);
                v = 0.5F - 0.5F * MathHelper.clamp(elevation / GLASS_HALF_PITCH_DEGREES, -1.0F, 1.0F);
            }
        }
        u += (roll.next() * 2.0F - 1.0F) * GLASS_JITTER;
        v += (roll.next() * 2.0F - 1.0F) * GLASS_JITTER;
        float angle = roll.next() * MathHelper.TAU;
        int style = Math.min(CRACK_STYLES - 1, (int) (roll.next() * CRACK_STYLES));
        return new VisorCrack(u, v, angle, style);
    }

    /**
     * H2 memo key (owner 2026-11-13: one attack source, one roll): the wearer and the {@link AttackSource}. Every
     * attempt of one source on one wearer reuses the first answer, whatever its death reason or origin (Savior settle
     * retries, Heavy Artillery, Second Strike, the TR fallback); two sources roll apart even from one shooter in one tick.
     * H2 记忆键（所有者 2026-11-13：一个攻击来源只掷一次骰）：佩戴者与 {@link AttackSource}。同一来源对同一佩戴者的每次尝试都
     * 沿用第一次的结果，无论死因或来源点（救世主结算重试、重炮、二连击、TR 后备效果）；两个来源即使出自同一射手、同一刻也分别掷骰。
     */
    public record ShotKey(UUID victim, AttackSource source) {
    }

    /**
     * One attack source, the best identity the attempt carries (owner 2026-11-13). In order: an open SparkWitch
     * {@code RangedOriginScope} ({@code frame}: a projectile entity's UUID, one shell's token, or the scope's own
     * identity), plus SparkStrength's innermost blast centre so a blast it chains inside that scope stays apart; else
     * SparkStrength's blast alone (one blast, one tick); else the call burst: the killer entity (its network id, so a
     * Magician's stand-in is not the Magician) in one server tick. The burst is the fallback for bare hitscan: it cannot
     * tell two scope-less weapons of one killer landing in one tick apart.
     * 一个攻击来源，取本次尝试所带的最佳身份（所有者 2026-11-13）。依次为：打开的 SparkWitch {@code RangedOriginScope}
     * （{@code frame}：投射物实体的 UUID、一发炮弹的令牌或该作用域自身的身份），并附上 SparkStrength 最内层爆心，使其在该作用域内
     * 连锁引发的爆炸仍各自独立；否则单独取 SparkStrength 爆炸（一次爆炸、一刻）；否则取调用批次：同一服务端刻内的击杀者实体（按其
     * 网络 id，因此魔术师的替身不等于魔术师本人）。调用批次是裸命中扫描的回退：它分不清同一击杀者在同一刻命中的两件无作用域武器。
     */
    public record AttackSource(@Nullable Object frame, @Nullable Vec3d blast, @Nullable Integer killer, long tick) {
        public static AttackSource of(@Nullable Object frame, @Nullable Vec3d blast, @Nullable Integer killer,
                                      long tick) {
            if (frame != null) {
                // A scoped source keeps its identity across ticks. / 有作用域的来源跨刻保持身份。
                return new AttackSource(frame, blast, null, 0L);
            }
            return blast != null ? new AttackSource(null, blast, null, tick) : new AttackSource(null, null, killer, tick);
        }
    }

    /** An injectable uniform roll in [0, 1). / 可注入的 [0, 1) 均匀随机数。 */
    @FunctionalInterface
    public interface FloatRoll {
        float next();
    }
}
