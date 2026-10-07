package dev.caecorthus.sparkwitch.roles.civilian.saint.flash;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Pure Holy Flash ({@code sparkwitch:holy_flash}) tuning: the Saint's re-buyable flashbang. Owner decisions
 * 2026-10-03: 75 coins, 10 s at the burst falling linearly to 3 s at the edge, walls block, the thrower and every
 * faction are affected, 15 s use cooldown, carry at most 3. Buff 2026-10-07: 6-block radius measured to the nearest
 * point of the body (was 4 to the eyes, which put a floor burst's 10 s out of reach), ×0.75 when facing away (was
 * halved), and the black mask holds for the first 60% (was 40%).
 * 圣光弹（{@code sparkwitch:holy_flash}）的纯数值：圣徒可重复购买的闪光弹。所有者 2026-10-03 决定：75 金币、
 * 落点处 10 秒并线性降到边缘 3 秒、墙体阻挡、投掷者与所有阵营都会被闪、使用冷却 15 秒、最多携带 3 个。
 * 2026-10-07 增强：半径 6 格并按到身体最近点计算（原为到眼睛 4 格，落地爆开时 10 秒根本够不到）、背对时 ×0.75
 * （原为减半）、全黑保持前 60%（原为 40%）。
 */
public final class HolyFlashRules {
    public static final Identifier ITEM_ID = SparkWitch.id("holy_flash");
    public static final Identifier ENTITY_ID = SparkWitch.id("holy_flash");
    public static final String SHOP_ENTRY_ID = "holy_flash";
    public static final int PRICE = 75;
    public static final int CARRY_LIMIT = 3;
    public static final int USE_COOLDOWN_TICKS = 15 * 20;
    /** Burst to the nearest point of the body's hitbox. / 爆点到身体碰撞箱最近点的距离。 */
    public static final double RADIUS = 6.0D;
    public static final int MAX_DURATION_TICKS = 10 * 20;
    public static final int EDGE_DURATION_TICKS = 3 * 20;
    public static final float FACING_AWAY_MULTIPLIER = 0.75F;
    /** Facing means the burst lies within this half-angle of the look vector. / 爆点位于视线该半角内即视为正对。 */
    public static final double FACING_HALF_ANGLE_DEGREES = 60.0D;

    // Client visual timeline (owner pick B: bright spot, full black, slow recovery).
    // 客户端视觉时间线（所有者选择方案 B：亮点、全黑、缓慢恢复）。
    public static final int SPOT_TICKS = 3;
    public static final float BLACK_HOLD_FRACTION = 0.6F;

    private HolyFlashRules() {
    }

    /**
     * Holy Flashes are thrown, burst and last only while Wathe's status is ACTIVE; STOPPING (still "running" for
     * Wathe) ends every flash so the ringing and muffle never spill into the round-end phase.
     * 圣光弹只在 Wathe 状态为 ACTIVE 时可投掷、爆开与持续；STOPPING（Wathe 仍视为“运行中”）会结束所有闪光，
     * 避免耳鸣与压音延续到回合结束阶段。
     */
    public static boolean isActivePhase(World world) {
        return GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
    }

    /**
     * Blind ticks for a player whose body is {@code distance} blocks from the burst
     * ({@link HolyFlashTargeting#bodyDistance}); 0 outside the radius.
     * 身体距爆点 {@code distance} 格（{@link HolyFlashTargeting#bodyDistance}）的玩家的致盲刻数；半径外为 0。
     */
    public static int durationTicks(double distance, boolean facing) {
        if (!(distance >= 0.0D) || distance > RADIUS) {
            return 0;
        }
        double t = MathHelper.clamp(distance / RADIUS, 0.0D, 1.0D);
        double ticks = MathHelper.lerp(t, MAX_DURATION_TICKS, EDGE_DURATION_TICKS);
        if (!facing) {
            ticks *= FACING_AWAY_MULTIPLIER;
        }
        return Math.max(1, (int) Math.round(ticks));
    }

    /** Whether the angle between the look vector and the burst direction is within the facing cone. / 视线与爆点方向夹角是否在正对锥内。 */
    public static boolean isFacing(double cosAngle) {
        return cosAngle >= Math.cos(Math.toRadians(FACING_HALF_ANGLE_DEGREES));
    }

    /**
     * Screen blackness for the B timeline at {@code elapsedTicks} of {@code totalTicks}: 1 while held, then an
     * ease-out fade to 0. The bright spot before it is drawn separately by the client.
     * 方案 B 时间线在 {@code totalTicks} 中第 {@code elapsedTicks} 刻的黑屏程度：保持阶段为 1，之后缓出淡到 0。
     * 之前的亮点由客户端单独绘制。
     */
    public static float blackness(float elapsedTicks, int totalTicks) {
        if (totalTicks <= 0 || elapsedTicks >= totalTicks) {
            return 0.0F;
        }
        float progress = MathHelper.clamp(elapsedTicks / totalTicks, 0.0F, 1.0F);
        if (progress <= BLACK_HOLD_FRACTION) {
            return 1.0F;
        }
        float fade = (progress - BLACK_HOLD_FRACTION) / (1.0F - BLACK_HOLD_FRACTION);
        float eased = 1.0F - (1.0F - fade) * (1.0F - fade);
        return 1.0F - eased;
    }

    /**
     * Tinnitus intensity (ring volume and muffle depth) at {@code elapsedTicks}: full at the burst, linear to 0.
     * 耳鸣强度（耳鸣音量与压音深度）：爆点时最大，线性降到 0。
     */
    public static float tinnitus(float elapsedTicks, int totalTicks) {
        if (totalTicks <= 0 || elapsedTicks >= totalTicks) {
            return 0.0F;
        }
        return 1.0F - MathHelper.clamp(elapsedTicks / totalTicks, 0.0F, 1.0F);
    }
}
