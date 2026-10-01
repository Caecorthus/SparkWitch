package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Potion Gunner constants and side-safe predicates. Every tunable gameplay value lives here so items, entities,
 * effects, the client scope, and tests share one source.
 * 药炮手常量与两端安全的判定。所有可调玩法数值集中于此，物品、实体、效果、客户端瞄准镜与测试共用同一来源。
 */
public final class PotionGunnerRules {
    public static final Identifier ROLE_ID = SparkWitch.id("potion_gunner");
    /** Potion violet, kept apart from the Accomplice's 0x6B338A. / 药水紫，与共犯的 0x6B338A 区分。 */
    public static final int COLOR = 0xB45CFF;

    public static final Identifier LAUNCHER_ID = SparkWitch.id("potion_launcher");
    public static final Identifier SHELL_ENTITY_ID = SparkWitch.id("potion_shell");
    /** Ordinary, non-forced TR shell kill. / 普通、非强制的 TR 炮弹击杀。 */
    public static final Identifier DEATH_REASON_ID = SparkWitch.id("potion_shell");
    /** Item-use replay record for one launcher shot. / 一次炮筒发射的物品使用回放记录。 */
    public static final Identifier FIRE_REPLAY_ID = SparkWitch.id("potion_launcher_fire");

    /** SparkFactionAPI affect action ids. / SparkFactionAPI 影响行为 id。 */
    public static final Identifier BLAST_ACTION = SparkWitch.id("potion_shell_blast");

    public static final int SHELL_MAX_STACK = 8;
    /** Minimum gap between two shots, against double clicks. / 两次发射的最小间隔，防止连点。 */
    public static final int FIRE_COOLDOWN_TICKS = 20;

    /**
     * Shell ballistics; the client scope ticks are computed from the same values. The shell flies dead straight at a
     * constant {@link #MUZZLE_SPEED} for its first {@link #FLAT_RANGE_BLOCKS} blocks of travel (owner rule: no drop
     * within 50 blocks); only after that do {@link #GRAVITY} and {@link #DRAG} apply.
     * 炮弹弹道；客户端刻度由同一组数值算出。炮弹在飞行的前 {@link #FLAT_RANGE_BLOCKS} 格内以恒定的
     * {@link #MUZZLE_SPEED} 笔直飞行（所有者规则：50 格内无下坠）；此后才受 {@link #GRAVITY} 与 {@link #DRAG} 影响。
     */
    public static final float MUZZLE_SPEED = 2.5F;
    public static final double FLAT_RANGE_BLOCKS = 50.0;
    public static final double GRAVITY = 0.03;
    public static final double DRAG = 0.99;
    /** A shell that hits nothing bursts in mid-air after this many ticks. / 未命中的炮弹在这么多刻后空爆。 */
    public static final int SHELL_LIFETIME_TICKS = 100;

    /** Gold paid per non-allied player caught in one blast. / 每颗炮弹每波及一名非己方玩家所得金币。 */
    public static final int HIT_REWARD = 15;

    public static final int SLOWNESS_II_AMPLIFIER = 1;
    /** GW-DK Blindness + Slowness II at the blast centre. / GW-DK 爆心处的失明与缓慢 II 时长。 */
    public static final int DK_MAX_TICKS = 140;
    /** GW-AC penalty as a fraction of each cooldown's nominal length at the centre. / GW-AC 爆心处按名义冷却计的惩罚比例。 */
    public static final double AC_FRACTION = 0.20;
    /** GW-MR gold removed at the centre; never below a zero balance. / GW-MR 爆心处扣除金币；余额不低于 0。 */
    public static final int MR_MAX_DEDUCTION = 150;
    /** TR fallback (Blindness, Slowness II, harmless burning) for a survivor. / TR 幸存者的失明、缓慢 II 与无伤燃烧时长。 */
    public static final int TR_FALLBACK_TICKS = 60;

    /**
     * Backblast: on every shot, the nearest player within this many blocks straight behind the launcher (line of sight,
     * any faction, never the gunner) takes one ordinary kill attempt — killed, or stopped by a shield.
     * 尾焰：每次发射时，炮筒正后方这么多格内、视线可达的最近一名玩家（不分阵营，不含药炮手本人）受到一次普通击杀判定——
     * 直接死亡，或被护盾挡下。
     */
    public static final double BACKBLAST_LENGTH = 4.0;
    /** Half-width of the backblast lane around its axis. / 尾焰判定通道相对轴线的半宽。 */
    public static final double BACKBLAST_HALF_WIDTH = 0.5;
    /** Ordinary, non-forced backblast kill. / 普通、非强制的尾焰击杀。 */
    public static final Identifier BACKBLAST_DEATH_REASON_ID = SparkWitch.id("potion_backblast");

    private PotionGunnerRules() {
    }

    public static boolean isPotionGunner(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }
}
