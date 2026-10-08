package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Stable contract: owner-approved USEC numbers (plan 2026-10-07, all questions answered), literal ids and side-neutral
 * pure predicates. Every duration is in ticks. Other USEC modules, client and server, read these values; they never
 * redefine them.
 * 稳定契约：已批准的 USEC 数值（2026-10-07 计划，所有问题已拍板）、字面量 id 与两端通用的纯判定。所有时长均以刻为单位。
 * 其他 USEC 模块（客户端与服务端）只读取这些值，从不重新定义。
 */
public final class UsecRules {
    // ---- Role ----
    public static final Identifier ROLE_ID = SparkWitch.id("usec");
    public static final int COLOR = 0xC8A96A;

    // ---- Items ----
    public static final Identifier RIFLE_ITEM_ID = SparkWitch.id("usec_rifle");
    public static final Identifier MAGAZINE_ITEM_ID = SparkWitch.id("usec_magazine");
    public static final Identifier FMJ_ITEM_ID = SparkWitch.id("usec_338_fmj");
    public static final Identifier AP_ITEM_ID = SparkWitch.id("usec_338_ap");
    public static final Identifier SUPPRESSOR_ITEM_ID = SparkWitch.id("usec_suppressor");

    // ---- Payload and component ids (literal; the payload records alias these) ----
    public static final Identifier FIRE_PAYLOAD_ID = SparkWitch.id("fire_usec_rifle");
    public static final Identifier ATTACHMENT_PAYLOAD_ID = SparkWitch.id("usec_attachment");
    public static final Identifier SCOPE_PAYLOAD_ID = SparkWitch.id("usec_scope");
    public static final Identifier IMPACTS_PAYLOAD_ID = SparkWitch.id("usec_bullet_impacts");
    public static final Identifier PLAYER_COMPONENT_ID = SparkWitch.id("usec_player");

    // ---- Sound ids (sounds.json keys are the paths) ----
    public static final Identifier SHOOT_SOUND_ID = SparkWitch.id("item.usec_rifle.shoot");
    public static final Identifier SHOOT_SUPPRESSED_SOUND_ID = SparkWitch.id("item.usec_rifle.shoot_suppressed");
    public static final Identifier BOLT_SOUND_ID = SparkWitch.id("item.usec_rifle.bolt");
    public static final Identifier MAGAZINE_SOUND_ID = SparkWitch.id("item.usec_rifle.magazine");
    public static final Identifier LOAD_ROUND_SOUND_ID = SparkWitch.id("item.usec_rifle.load_round");
    /** Subtle scope-in sound (owner, 2026-10-07): heard by the shooter and nearby players. / 细微的开镜声（所有者 2026-10-07）：自己与附近玩家可闻。 */
    public static final Identifier SCOPE_SOUND_ID = SparkWitch.id("item.usec_rifle.scope");

    // ---- Shop (D11; rounds are sold one at a time) ----
    public static final String MAGAZINE_ENTRY_ID = "usec_magazine";
    public static final String FMJ_ENTRY_ID = "usec_338_fmj";
    public static final String AP_ENTRY_ID = "usec_338_ap";
    public static final String SUPPRESSOR_ENTRY_ID = "usec_suppressor";
    public static final int MAGAZINE_PRICE = 25;
    public static final int FMJ_PRICE = 50;
    public static final int AP_PRICE = 150;
    public static final int SUPPRESSOR_PRICE = 50;
    public static final int SUPPRESSOR_STOCK = 1;
    public static final int MAGAZINE_CAPACITY = 5;

    // ---- Economy (Q1/Q1+: start at 0, +50 per task, +5 every 10 s with no cap) ----
    public static final int INITIAL_MONEY = 0;
    public static final int TASK_REWARD = 50;
    public static final int PASSIVE_INCOME = 5;
    public static final int PASSIVE_INCOME_INTERVAL_TICKS = 200;

    // ---- Stamina (twice the police cap and regeneration; stacks with traits) ----
    public static final int MAX_SPRINT_TICKS = GameConstants.getInTicks(0, 20);
    public static final double STAMINA_REGEN_FACTOR = 2.0;

    // ---- Cooldowns ----
    public static final int ROUND_START_COOLDOWN_TICKS = 1200;
    public static final int BOLT_TICKS = 40;
    /** SparkTraits Quick Reload: bolt 2 s x 0.7 = 1.4 s (Q7 default). / 快速装填：拉栓 2 秒 x 0.7 = 1.4 秒（Q7 默认）。 */
    public static final double FAST_RELOAD_BOLT_FACTOR = 0.7;

    // ---- Ballistics (Q3+/D4; shared by the server tracer and the client reticle through UsecBallistics) ----
    /** FMJ: straight line, stops at the first block. / FMJ：直线，遇到第一个方块即停。 */
    public static final double FMJ_RANGE = 50.0;
    /** AP: 100% energy equals this many blocks of open flight. / AP：100% 能量等于这么多格的无障碍飞行。 */
    public static final double AP_RANGE = 200.0;
    /** Energy fraction spent per block flown (0.5%). / 每飞 1 格消耗的能量比例（0.5%）。 */
    public static final double AP_FLIGHT_COST_PER_BLOCK = 1.0 / AP_RANGE;
    /** Energy fraction spent per penetrated block (37.5%, i.e. -75 blocks). / 每穿透 1 个方块消耗的能量比例（37.5%，即 -75 格）。 */
    public static final double AP_PENETRATION_COST = 0.375;
    /**
     * Sink-rate table: band i starts at {@code AP_SINK_BAND_STARTS[i]} (energy fraction spent) and sinks
     * {@code AP_SINK_RATES[i]} blocks per block flown, up to the next start (the last band ends at 100%).
     * 下沉率分段表：第 i 段从已用能量 {@code AP_SINK_BAND_STARTS[i]} 开始，每飞 1 格下沉 {@code AP_SINK_RATES[i]} 格，
     * 直到下一段开始（最后一段止于 100%）。
     */
    public static final List<Double> AP_SINK_BAND_STARTS = List.of(0.0, 0.375, 0.625, 0.875);
    public static final List<Double> AP_SINK_RATES = List.of(0.0, 0.01, 0.025, 0.04);
    /**
     * Bounds of the SparkTraits Marksman multiplier m: FMJ range x m, AP per-block flight cost / m (penetration cost
     * unchanged). / SparkTraits 精确枪手倍率 m 的上下限：FMJ 射程 x m，AP 每格飞行耗能 / m（穿透耗能不变）。
     */
    public static final double MIN_MARKSMAN_MULTIPLIER = 1.0;
    public static final double MAX_MARKSMAN_MULTIPLIER = 1.3;
    /** Scope FOV multipliers: 4x, then 8x (Shift + right-click toggles). / 开镜视野倍率：4 倍、8 倍（Shift + 右键切换）。 */
    public static final float[] ZOOM_FOV_MULTIPLIERS = {0.25F, 0.125F};
    /** Rewound player hitboxes are expanded by this much. / 倒回的玩家碰撞箱外扩量。 */
    public static final double HIT_MARGIN = 0.1;

    // ---- Shield piercing (O3, owner 2026-10-07) ----
    /** Shield layers one FMJ round pierces. / 一发 FMJ 可击穿的护盾层数。 */
    public static final int FMJ_SHIELD_PIERCE = 2;
    /** Shield layers one AP round pierces. / 一发 AP 可击穿的护盾层数。 */
    public static final int AP_SHIELD_PIERCE = 5;
    /**
     * Extra layers on a SparkTraits Heavy Artillery shot (within its 5-block reach). / SparkTraits 重炮手射击（5 格内）
     * 额外击穿的层数。
     */
    public static final int HEAVY_ARTILLERY_SHIELD_PIERCE_BONUS = 1;

    // ---- Sound (D12, Q6; louder re-master 2026-10-08) ----
    /**
     * Gain is clamped to 1, so a volume above 1 only widens the linear roll-off radius (16 x volume): 192 blocks.
     * 增益上限为 1，音量大于 1 只会扩大线性衰减半径（16 × 音量）：192 格。
     */
    public static final float SHOT_VOLUME = 12.0F;
    /**
     * 64 blocks: about -3 dB at 20 blocks, still shorter-reaching than the revolver's 80.
     * 64 格：20 格处约 -3 dB，仍比左轮的 80 格近。
     */
    public static final float SUPPRESSED_SHOT_VOLUME = 4.0F;
    /**
     * The suppressed .ogg is rendered at its final pitch (full band to 18.5 kHz), so it must play at 1.0; a higher
     * pitch would shift it and could alias in OpenAL's resampler.
     * 消音枪声 .ogg 已按最终音调渲染（全频带至 18.5 kHz），必须以 1.0 播放；更高音调会使其变调，并可能在 OpenAL 重采样中混叠。
     */
    public static final float SUPPRESSED_PITCH = 1.0F;
    /** Fixed broadcast range of the scope-in sound, in blocks (Seeker motor precedent). / 开镜声的固定广播范围（格）。 */
    public static final float SCOPE_SOUND_RANGE = 8.0F;
    public static final float SCOPE_SOUND_VOLUME = 0.8F;
    /** Minimum ticks between two scope-in sounds of one player, so rapid toggling cannot spam it. / 同一玩家两次开镜声的最小间隔刻。 */
    public static final int SCOPE_SOUND_MIN_INTERVAL_TICKS = 8;
    public static final double BLIND_LOUD_RANGE_FACTOR = 2.0;
    public static final double BLIND_SUPPRESSED_RANGE_FACTOR = 0.5;

    // ---- Cracks (D13; visual only, never breaks a block) ----
    public static final int CRACK_HOLD_TICKS = 100;
    public static final int CRACK_HEAL_STEP_TICKS = 20;

    private UsecRules() {
    }

    public static boolean isUsecId(@Nullable Identifier roleId) {
        return ROLE_ID.equals(roleId);
    }

    public static boolean isUsec(@Nullable Role role) {
        return role != null && isUsecId(role.identifier());
    }

    /**
     * Scope FOV multiplier for a zoom level (0 = 4x, 1 = 8x); out-of-range levels clamp. Read through this instead of
     * writing to the array. / 指定倍率档位的开镜视野倍率（0 = 4 倍，1 = 8 倍）；越界档位会被钳制。请通过此方法读取，不要写入数组。
     */
    public static float zoomFovMultiplier(int level) {
        int clamped = Math.max(0, Math.min(ZOOM_FOV_MULTIPLIERS.length - 1, level));
        return ZOOM_FOV_MULTIPLIERS[clamped];
    }
}
