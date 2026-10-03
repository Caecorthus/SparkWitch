package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * Pure decisions for standing on the Deep Dark Zone: check cadence, who counts as standing, the short effect refresh
 * and which thrower acts. No Minecraft state, so every row of the decision table is unit-tested.
 * 站在深暗领域上的纯判定：检查节奏、谁算站在上面、短效果刷新规则以及由哪位投掷者作为施加者。不读取 Minecraft 状态，
 * 因此判定表的每一行都可单元测试。
 */
public final class DeepDarkZoneStandingRules {
    /**
     * A zone effect is re-applied once at most half of its refresh length remains: with checks every
     * {@link AbyssListenerRules#ZONE_CHECK_INTERVAL_TICKS} ticks it never lapses while the player stands, and it ends
     * 1-2 s after the player steps off (the remaining duration then lies in (threshold - interval, refresh]).
     * 剩余时长不超过刷新时长的一半时重新施加：每 {@link AbyssListenerRules#ZONE_CHECK_INTERVAL_TICKS} 刻检查一次，
     * 站立期间效果不会断档；离开后效果在 1-2 秒内结束（此时剩余时长落在 (阈值 - 间隔, 刷新时长] 内）。
     */
    public static final int REFRESH_THRESHOLD_TICKS = AbyssListenerRules.ZONE_EFFECT_REFRESH_TICKS / 2;

    private DeepDarkZoneStandingRules() {
    }

    /** What a player's position means for the zone this check. / 本次检查中玩家位置对领域的意义。 */
    public enum Standing {
        /** Not a participant, airborne, or the stepping block is not converted. / 非参与者、腾空或脚下方块未转换。 */
        NONE,
        /** A witch-faction ally standing on converted blocks: Speed. / 站在转换方块上的魔女阵营队友：加速。 */
        ALLY,
        /** Anyone else standing on converted blocks: Slowness and exposure if the veto allows. / 其他人：缓慢与暴露（需通过否决）。 */
        NON_ALLY
    }

    public static boolean isCheckTick(long worldTime) {
        return Math.floorMod(worldTime, AbyssListenerRules.ZONE_CHECK_INTERVAL_TICKS) == 0;
    }

    /**
     * Standing = participant, {@code isOnGround()}, and the stepping block ({@code getSteppingPos()}, the block vanilla
     * also picks for the step sound) is converted. The suppliers run lazily and in that order, so the zone registry and
     * the faction lookup are only queried for grounded participants.
     * 站立 = 参与者、{@code isOnGround()}，且脚下方块（{@code getSteppingPos()}，与原版脚步声选用的方块相同）已被转换。
     * 各提供者按此顺序惰性求值，因此只对着地的参与者查询领域登记表与阵营。
     */
    public static Standing classify(
            boolean participant,
            boolean onGround,
            BooleanSupplier steppingConverted,
            BooleanSupplier ally
    ) {
        if (!participant || !onGround || !steppingConverted.getAsBoolean()) {
            return Standing.NONE;
        }
        return ally.getAsBoolean() ? Standing.ALLY : Standing.NON_ALLY;
    }

    /**
     * Whether a zone effect of {@code wantedAmplifier} must be (re)applied: missing, weaker, or a finite instance with
     * at most {@link #REFRESH_THRESHOLD_TICKS} left. A stronger or infinite instance from another source is left alone
     * until it runs short (vanilla then keeps the weaker zone instance as its hidden follow-up).
     * 是否需要（重新）施加强度为 {@code wantedAmplifier} 的领域效果：缺失、较弱，或剩余不超过
     * {@link #REFRESH_THRESHOLD_TICKS} 刻的有限效果。来自其他来源的更强或无限效果保持不动，直到其所剩无几
     * （之后原版会把较弱的领域效果保存为其隐藏后续效果）。
     */
    public static boolean needsRefresh(
            boolean present,
            int amplifier,
            int remainingTicks,
            boolean infinite,
            int wantedAmplifier
    ) {
        if (!present || amplifier < wantedAmplifier) {
            return true;
        }
        return !infinite && remainingTicks <= REFRESH_THRESHOLD_TICKS;
    }

    /**
     * The acting thrower: the first owner, in throw order, that {@code livingOnline} resolves (online and still a
     * living participant), else {@code null}. Plan default N19: a zone outlives its thrower, so a dead or departed
     * thrower neither switches it off nor lends it an SFA veto or a Wraith-isolated effect source.
     * 施加者：按投掷顺序第一个能被 {@code livingOnline} 解析的投掷者（在线且仍为存活参与者），否则为 {@code null}。
     * 方案默认 N19：领域比投掷者活得久，已死亡或离开的投掷者既不会关闭领域，也不会为其带来 SFA 否决或受冤魂隔离的效果来源。
     */
    public static @Nullable <P> P actingOwner(List<UUID> owners, Function<UUID, P> livingOnline) {
        for (UUID owner : owners) {
            P player = livingOnline.apply(owner);
            if (player != null) {
                return player;
            }
        }
        return null;
    }
}
