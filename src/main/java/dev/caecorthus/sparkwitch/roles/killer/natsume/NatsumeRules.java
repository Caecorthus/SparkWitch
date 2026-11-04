package dev.caecorthus.sparkwitch.roles.killer.natsume;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;

/**
 * 夏目安安 Natsume Anan — killer hypnotist.
 * <p>Owner spec 2026-11-03: identity colour {@code C5B4FF}; the ability key puts a target under hypnosis for 12 s
 * (60 s initial, 90 s afterwards) at up to 45 blocks. While hypnotised the victim is blinded, loses 4% sanity per
 * second, cannot use skills or items, and their instinct highlight is disabled.
 * 夏目安安——杀手阵营的催眠师。
 * <p>所有者规格 2026-11-03：身份色 {@code C5B4FF}；按技能键使目标进入 12 秒催眠（初始 60 秒，之后 90 秒），
 * 作用半径 45 格。被催眠者失明、每秒失去 4% 理智、无法使用技能与道具，本能透视也被禁用。
 */
public final class NatsumeRules {

    public static final Identifier ROLE_ID = SparkWitch.id("natsume_anan");

    /** Identity colour C5B4FF. 身份色 C5B4FF。 */
    public static final int COLOR = 0xC5B4FF;

    /** Cooldowns: 60 s at round start, 90 s after that. 冷却：开局 60 秒，之后 90 秒。 */
    public static final int INITIAL_COOLDOWN_TICKS = 60 * 20;
    public static final int COOLDOWN_TICKS = 90 * 20;

    /** Hypnosis lasts 12 s on civilians, 8 s on police/killers/desperadoes. 平民 12 秒，警职/杀手/亡命徒 8 秒。 */
    public static final int HYPNOSIS_TICKS = 12 * 20;
    public static final int HYPNOSIS_TICKS_HOSTILE = 8 * 20;
    /** Melee range only, and the caster must actually see the target. 只能近身，且必须真的看得见目标。 */
    public static final double RANGE = 3.0D;

    /** 4% sanity drained per second of hypnosis. 催眠期间每秒扣 4% 理智。 */
    public static final float SANITY_PER_SECOND = 0.04F;

    private NatsumeRules() {
    }
}
