package dev.caecorthus.sparkwitch.roles.witch.abysslistener;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;

/**
 * Abyss Listener (聆渊者) constants and pure predicates: a witch-faction special accomplice with a Warden/sculk kit.
 * Every tuning value lives here so the owner can retune without hunting through services. Server-authoritative;
 * the client only mirrors values it is sent.
 * 聆渊者（魔女阵营特殊共犯，监守者/幽匿主题）的常量与纯判定。所有数值集中于此，便于调参。服务端权威；客户端只镜像收到的值。
 */
public final class AbyssListenerRules {
    public static final Identifier ROLE_ID = SparkWitch.id("abyss_listener");
    /** Role theme color (dark sculk teal). / 职业主题色（幽匿暗青）。 */
    public static final int COLOR = 0x0B5E78;

    // --- Warden's Shriek (role skill, shared skill key) / 监守之啸（职业技能，共享技能键） ---
    public static final Identifier SHRIEK_SKILL_ID = SparkWitch.id("wardens_shriek");
    public static final Identifier SHRIEK_ACTION_ID = SparkWitch.id("abyss_listener_shriek");
    public static final int SHRIEK_INITIAL_COOLDOWN_TICKS = 90 * 20;
    public static final int SHRIEK_COOLDOWN_TICKS = 60 * 20;
    /**
     * Mana per cast; the Abyss Listener gains mana like the Grand Witch. The skill definition only displays it; the
     * use handler spends it. / 每次施放消耗的魔力；聆渊者与大魔女一样获得魔力。技能定义只用于显示，由使用处理器实际扣除。
     */
    public static final int SHRIEK_MANA_COST = 75;
    public static final double SHRIEK_RADIUS = 8.0;
    public static final int SHRIEK_SLOWNESS_TICKS = 5 * 20;
    /** Slowness II. / 缓慢 II。 */
    public static final int SHRIEK_SLOWNESS_AMPLIFIER = 1;
    /** 40 sanity points (Wathe mood 1 point = 0.01). / 40 点理智（Wathe 1 点 = 0.01）。 */
    public static final float SHRIEK_SANITY_LOSS = 0.40F;
    public static final int SHRIEK_FORCED_COOLDOWN_TICKS = 5 * 20;

    // --- Shriek Gun / 啸音铳 ---
    public static final Identifier GUN_ITEM_ID = SparkWitch.id("shriek_gun");
    public static final Identifier GUN_ACTION_ID = SparkWitch.id("abyss_listener_gun");
    /** Written with vanilla ItemCooldownManager.set, so SparkTraits Fast Hands applies (owner spec). / 原版写入，吃快手。 */
    public static final int GUN_INITIAL_COOLDOWN_TICKS = 60 * 20;
    public static final int GUN_COOLDOWN_TICKS = 30 * 20;
    public static final double GUN_RANGE = 12.0;
    /** Horizontal speed (blocks/tick) and lift for an enemy hit; vanilla syncs at most 3.9 per axis. / 敌人击退。 */
    public static final double GUN_ENEMY_KNOCKBACK = 2.0;
    public static final double GUN_ENEMY_LIFT = 0.45;
    /** Farther but flatter for allies, so they rarely clear a railing (plan §8-1). / 队友更远更平。 */
    public static final double GUN_ALLY_KNOCKBACK = 3.0;
    public static final double GUN_ALLY_LIFT = 0.2;
    public static final float GUN_SANITY_LOSS = 0.60F;
    public static final int GUN_DEBUFF_TICKS = 2 * 20;
    /** Slowness II. / 缓慢 II。 */
    public static final int GUN_SLOWNESS_AMPLIFIER = 1;
    public static final int GUN_FORCED_COOLDOWN_TICKS = 2 * 20;
    public static final int GUN_ALLY_SPEED_TICKS = 2 * 20;
    /** Speed III. / 速度 III。 */
    public static final int GUN_ALLY_SPEED_AMPLIFIER = 2;

    // --- Deep Dark Spore Flask and Deep Dark Zone / 深暗孢瓶与深暗领域 ---
    public static final Identifier FLASK_ITEM_ID = SparkWitch.id("deep_dark_spore_flask");
    public static final Identifier FLASK_ENTITY_ID = SparkWitch.id("deep_dark_spore_flask");
    public static final Identifier ZONE_ACTION_ID = SparkWitch.id("abyss_listener_zone");
    public static final String FLASK_SHOP_ENTRY_ID = "deep_dark_spore_flask";
    public static final int FLASK_PRICE = 75;
    /**
     * Flood-fill reach through air from the landing cell, walls and closed doors block (D4; owner D14: 8 → 10).
     * 沿空气蔓延的最远距离，墙与关着的门会挡住（D4；D14 由 8 改为 10）。
     */
    public static final int ZONE_RADIUS = 10;
    /**
     * Owner D14: the whole zone converts on the landing tick (no spread phase), then holds this long.
     * D14：整个领域在落地那一刻全部转换（没有蔓延阶段），随后保持这么久。
     */
    public static final int ZONE_HOLD_TICKS = 15 * 20;
    public static final int ZONE_RESTORE_TICKS = 5 * 20;
    public static final int ZONE_CHECK_INTERVAL_TICKS = 5;
    public static final int ZONE_EFFECT_REFRESH_TICKS = 2 * 20;
    /** Slowness III for non-allies standing on converted blocks. / 敌人站上去缓慢 III。 */
    public static final int ZONE_SLOWNESS_AMPLIFIER = 2;
    /** Speed III for allies standing on converted blocks. / 队友站上去速度 III。 */
    public static final int ZONE_ALLY_SPEED_AMPLIFIER = 2;
    /** Exposure lasts this long after the last standing check (owner-only synced). / 暴露标记持续时间。 */
    public static final int ZONE_EXPOSURE_TICKS = 10;
    /** Sanity drain multiplier while exposed (D7: ×15, the pseudo task counts as one task). / 暴露时理智下降倍率。 */
    public static final int ZONE_DRAIN_MULTIPLIER = 15;
    /** The pseudo task counts as this many extra tasks for the drain (D7). / 临时任务计为一个任务。 */
    public static final int ZONE_PSEUDO_TASKS = 1;
    /** 「快离开这里！！！」 colors; the line blinks between them every {@link #PSEUDO_TASK_BLINK_TICKS}. / 闪烁颜色。 */
    public static final int PSEUDO_TASK_COLOR = 0x29DFEB;
    public static final int PSEUDO_TASK_DIM_COLOR = 0x0B5E78;
    public static final int PSEUDO_TASK_BLINK_TICKS = 10;

    private AbyssListenerRules() {
    }

    public static boolean isAbyssListenerId(Identifier roleId) {
        return ROLE_ID.equals(roleId);
    }

    public static boolean isAbyssListener(Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * Per-tick extra mood drain multiplier for an exposed player: (real tasks + pseudo) × 15 relative to one task.
     * Returned as the factor applied to Wathe's per-task drain; 0 real tasks still drains like 15 tasks.
     * 暴露玩家每 tick 的下降倍率：(真实任务数 + 临时任务) × 15，相对单个任务的速度。
     */
    public static float exposedDrainTasks(int realTasks) {
        return (Math.max(0, realTasks) + ZONE_PSEUDO_TASKS) * (float) ZONE_DRAIN_MULTIPLIER;
    }
}
