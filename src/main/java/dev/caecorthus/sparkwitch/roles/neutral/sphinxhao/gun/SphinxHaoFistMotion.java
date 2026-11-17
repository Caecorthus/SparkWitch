package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun;

import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;

/**
 * Pure timeline of the spring fist ({@code SphinxHaoFistEntity}), shared by the server (sounds, discard) and the client
 * renderer: at full reach the instant it exists, held for {@link SphinxHaoRules#FIST_HOLD_TICKS}, back in
 * {@link SphinxHaoRules#FIST_RETRACT_TICKS}. The extension is the fraction of the muzzle-to-target distance covered.
 * 弹簧拳头（{@code SphinxHaoFistEntity}）的纯时间线，服务端（音效、移除）与客户端渲染共用：出现的一瞬间即伸到最远，
 * 停留 {@link SphinxHaoRules#FIST_HOLD_TICKS} 刻，{@link SphinxHaoRules#FIST_RETRACT_TICKS} 刻收回。
 * 伸展量是从枪口到目标距离中已覆盖的比例。
 */
public final class SphinxHaoFistMotion {
    /** Age at which the fist starts back (the retract sound). / 拳头开始收回的刻（收回音效）。 */
    public static final int RETRACT_AGE = SphinxHaoRules.FIST_HOLD_TICKS;
    /** Age at which the entity is discarded. / 实体被移除的刻。 */
    public static final int END_AGE = SphinxHaoRules.FIST_LIFETIME_TICKS;

    private SphinxHaoFistMotion() {
    }

    /**
     * Extension in [0, 1] at {@code age} ticks (fractional, the render's {@code age + tickDelta}): 1 from age 0 (no
     * travel, the hit is already resolved), then a linear pull back. Negative or NaN ages are 0; ages past the end are 0.
     * {@code age} 刻（可为小数，即渲染时的 {@code age + tickDelta}）时的伸展量 [0, 1]：从第 0 刻起即为 1（没有飞行过程，
     * 命中早已结算），然后线性收回。负数或 NaN 为 0；超过结束时刻为 0。
     */
    public static float extension(float age) {
        if (!(age >= 0.0F) || age >= END_AGE) {
            return 0.0F;
        }
        if (age < RETRACT_AGE) {
            return 1.0F;
        }
        return 1.0F - (age - RETRACT_AGE) / SphinxHaoRules.FIST_RETRACT_TICKS;
    }

    /** Whether the fist still exists at whole age {@code age}. / 整数刻 {@code age} 时拳头是否仍存在。 */
    public static boolean expired(int age) {
        return age >= END_AGE;
    }
}
