package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable values and pure gates of the Black Raven disguise: timings, the role-id sets that decide
 * which absent good roles can be claimed, the Tab B pool flags, and the switch verdict order.
 * Server authoritative; the client only mirrors synced results and never re-decides a switch.
 * 黑羽鸦伪装的稳定数值与纯规则：时间、决定可伪装未登场好人职业的身份集合、Tab B 名单标记，
 * 以及变身判定顺序。服务端为权威；客户端只镜像同步结果，从不自行判定变身。
 */
public final class BlackRavenDisguiseRules {
    /** Revert target and the Raven's own stash key. / 恢复目标与黑羽鸦自身存档键。 */
    public static final Identifier BLACK_RAVEN_ID = BlackRavenRules.ROLE_ID;
    /** Component id, replay skill id, and payload namespace root. / 组件 id、回放技能 id 与数据包命名根。 */
    public static final Identifier DISGUISE_ID = SparkWitch.id("black_raven_disguise");
    public static final Identifier MASK_ITEM_ID = SparkWitch.id("black_raven_mask");

    public static final int UNLOCK_DELAY_TICKS = 60 * 20;
    public static final int SWITCH_COOLDOWN_TICKS = 20 * 20;
    /** Shop purchases and inventory clicks are ignored this long after a change. / 身份变化后此时长内忽略购买与背包点击。 */
    public static final int POST_SWITCH_GUARD_TICKS = 20;
    public static final int SESSION_TTL_TICKS = 60 * 20;
    public static final int FALLBACK_CHECK_INTERVAL_TICKS = 20;
    public static final int TASK_REWARD = 50;

    public static final int MAX_IDENTITIES = 64;
    public static final int MAX_POOL_ENTRIES = 64;
    public static final int MAX_STASH_SLOT_STACKS = 41;
    public static final int MAX_STASH_OVERFLOW = 16;
    public static final int PLAYER_SLOT_COUNT = 41;
    public static final int MAX_ROLE_ID_LENGTH = 128;

    public static final String MESSAGE_PREFIX = "message.sparkwitch.black_raven.disguise.";

    /** The 19 good roles that may ever be claimed, in design order. / 允许伪装的 19 个好人职业（设计顺序）。 */
    public static final Set<Identifier> ALLOWED = orderedSet(
            noelles("conductor"),
            noelles("attendant"),
            noelles("awesome_binglus"),
            noelles("mermaid"),
            noelles("time_keeper"),
            noelles("waiter"),
            noelles("reporter"),
            sparkwitch("tarot_reader"),
            sparkwitch("orthopedist"),
            noelles("bartender"),
            noelles("recaller"),
            noelles("noisemaker"),
            noelles("spiritualist"),
            sparkwitch("prophet"),
            sparkwitch("perfumer"),
            noelles("toxicologist"),
            noelles("engineer"),
            noelles("detective"),
            noelles("professor")
    );

    /** Good roles that are never claimable even when absent. / 即使未登场也永远不可伪装的好人职业。 */
    public static final Set<Identifier> DENYLIST = orderedSet(
            sparkwitch("saint"),
            noelles("voodoo"),
            noelles("survival_master"),
            noelles("coroner"),
            noelles("bodyguard"),
            sparkwitch("apprentice_witch"),
            sparkwitch("wind_spirit"),
            sparkwitch("guardian_angel"),
            sparkwitch("vendetta"),
            sparkwitch("pig_god"),
            sparkwitch("blind"),
            noelles("demon_hunter"),
            noelles("undercover"),
            wathe("civilian"),
            wathe("discovery_civilian")
    );

    /** Literal police ids, checked alongside SparkFactionAPI's PoliceRoles. / 与 SparkFactionAPI PoliceRoles 并行检查的警察 id。 */
    public static final Set<Identifier> POLICE_IDS = orderedSet(
            wathe("vigilante"),
            wathe("veteran"),
            sparkwitch("judge"),
            sparkwitch("emma"),
            sparkwitch("control_expert"),
            sparkwitch("seeker")
    );

    /** Roles whose adapters ship in batch 1; the rest of ALLOWED shows as UNSUPPORTED. / 第一批已上线适配器的职业。 */
    public static final Set<Identifier> BATCH1_SHIPPED = orderedSet(
            noelles("conductor"),
            noelles("attendant"),
            noelles("awesome_binglus"),
            noelles("mermaid"),
            noelles("time_keeper"),
            noelles("waiter"),
            noelles("reporter"),
            sparkwitch("tarot_reader"),
            sparkwitch("orthopedist")
    );

    /** Stacks that never leave their slot during a swap. / 交换时永远留在原槽位的物品。 */
    public static final Set<Identifier> PINNED_ITEM_IDS = orderedSet(
            wathe("key"),
            wathe("letter"),
            MASK_ITEM_ID
    );

    private BlackRavenDisguiseRules() {
    }

    /**
     * Tab B pool flag; the byte code is the NBT/sync value and must stay stable.
     * Tab B 名单标记；字节码是 NBT/同步值，必须保持稳定。
     */
    public enum PoolFlag {
        SELECTABLE(0, 0, null),
        POLICE(1, 2, "police"),
        DENYLISTED(2, 3, "denylisted"),
        UNSUPPORTED(3, 1, "unsupported"),
        UNKNOWN(4, 4, "unknown");

        private final byte code;
        private final int displayRank;
        private final @Nullable String reasonSuffix;

        PoolFlag(int code, int displayRank, @Nullable String reasonSuffix) {
            this.code = (byte) code;
            this.displayRank = displayRank;
            this.reasonSuffix = reasonSuffix;
        }

        public byte code() {
            return code;
        }

        /** Row group order: SELECTABLE, UNSUPPORTED, POLICE, DENYLISTED, UNKNOWN. / 行分组顺序。 */
        public int displayRank() {
            return displayRank;
        }

        /** Greyed-row tooltip key, or null for a selectable row. / 灰色行提示键；可选行为 null。 */
        public @Nullable String reasonKey() {
            return reasonSuffix == null ? null : "screen.sparkwitch.black_raven_ledger.absent.reason." + reasonSuffix;
        }

        /** Unknown codes decode fail-closed. / 未知编码按失败关闭解码。 */
        public static PoolFlag fromCode(int code) {
            for (PoolFlag flag : values()) {
                if (flag.code == code) {
                    return flag;
                }
            }
            return UNKNOWN;
        }
    }

    /** One Tab B snapshot row, stored in role order. / Tab B 快照中的一行，按职业顺序存储。 */
    public record PoolEntry(Identifier id, PoolFlag flag) {
        public PoolEntry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(flag, "flag");
        }
    }

    /**
     * Ordered verdict of one switch request; checks run in declaration order.
     * 单次变身请求的判定结果；按声明顺序检查。
     */
    public enum SwitchVerdict {
        ALLOWED(null),
        /**
         * Expired or consumed mask session. Not silent: an expired click tells the Raven to use the mask again.
         * 面具会话已过期或已消费。不静默：过期点击会提示黑羽鸦再次使用假面。
         */
        INVALID_SESSION("session_expired"),
        NOT_ELIGIBLE(null),
        LOCKED("locked"),
        COOLDOWN("cooldown"),
        UNAVAILABLE("unavailable"),
        WINDOW_ACTIVE("window_active"),
        BLOCKED("blocked");

        private final @Nullable String messageSuffix;

        SwitchVerdict(@Nullable String messageSuffix) {
            this.messageSuffix = messageSuffix;
        }

        /** Actionbar key, or null when the refusal is silent. / 动作栏提示键；静默拒绝时为 null。 */
        public @Nullable String messageKey() {
            return messageSuffix == null ? null : MESSAGE_PREFIX + messageSuffix;
        }
    }

    /**
     * Inputs of one switch request, already resolved by the server service.
     * {@code eligible} covers: game running, playing and alive, not spectator/creative, raw role
     * black_raven, and a component bound to the current match.
     * 服务端已解析的变身请求输入；eligible 涵盖对局进行、存活参与、非旁观/创造、真实职业为黑羽鸦且组件绑定当前对局。
     */
    public record SwitchCheck(
            boolean sessionValid,
            boolean eligible,
            long now,
            long unlockAt,
            long nextSwitchAt,
            @Nullable Identifier current,
            Identifier target,
            boolean targetSelectable,
            boolean windowActive,
            boolean swallowed,
            boolean feared,
            boolean skillBlocked
    ) {
    }

    public static SwitchVerdict canSwitch(SwitchCheck check) {
        if (!check.sessionValid()) {
            return SwitchVerdict.INVALID_SESSION;
        }
        if (!check.eligible()) {
            return SwitchVerdict.NOT_ELIGIBLE;
        }
        if (check.now() < check.unlockAt()) {
            return SwitchVerdict.LOCKED;
        }
        if (check.now() < check.nextSwitchAt()) {
            return SwitchVerdict.COOLDOWN;
        }
        Identifier target = check.target();
        boolean disguised = check.current() != null;
        boolean available = BLACK_RAVEN_ID.equals(target)
                ? disguised
                : check.targetSelectable() && !target.equals(check.current());
        if (!available) {
            return SwitchVerdict.UNAVAILABLE;
        }
        if (check.windowActive()) {
            return SwitchVerdict.WINDOW_ACTIVE;
        }
        if (check.swallowed() || check.feared() || check.skillBlocked()) {
            return SwitchVerdict.BLOCKED;
        }
        return SwitchVerdict.ALLOWED;
    }

    /**
     * Flag precedence for a listed role: police, denylisted, shipped allowed, allowed, unknown.
     * 已列出职业的标记优先级：警察、禁用、已上线允许、允许、未知。
     */
    public static PoolFlag classify(Identifier roleId, boolean policeByApi, boolean shipped) {
        if (policeByApi || POLICE_IDS.contains(roleId)) {
            return PoolFlag.POLICE;
        }
        if (DENYLIST.contains(roleId)) {
            return PoolFlag.DENYLISTED;
        }
        if (ALLOWED.contains(roleId)) {
            return shipped ? PoolFlag.SELECTABLE : PoolFlag.UNSUPPORTED;
        }
        return PoolFlag.UNKNOWN;
    }

    public static boolean isAllowed(@Nullable Identifier roleId) {
        return roleId != null && ALLOWED.contains(roleId);
    }

    public static boolean isPinnedItemId(@Nullable Identifier itemId) {
        return itemId != null && PINNED_ITEM_IDS.contains(itemId);
    }

    public static long unlockAt(long roundStartAt) {
        return roundStartAt + UNLOCK_DELAY_TICKS;
    }

    public static long nextSwitchAt(long changedAt) {
        return changedAt + SWITCH_COOLDOWN_TICKS;
    }

    /** True while shop purchases and slot clicks are ignored. / 购买与槽位点击被忽略期间为 true。 */
    public static boolean isWithinPostSwitchGuard(long lastChangeAt, long now) {
        return lastChangeAt > 0L && now >= lastChangeAt && now - lastChangeAt < POST_SWITCH_GUARD_TICKS;
    }

    /**
     * First-entry cooldown aligned to the round clock: what a real holder would have left now.
     * 与本局时钟对齐的首次进入冷却：真实持有者此刻剩余的冷却。
     */
    public static int aligned(int initialTicks, long roundStartAt, long now) {
        long elapsed = Math.max(0L, now - roundStartAt);
        return (int) Math.clamp((long) Math.max(0, initialTicks) - elapsed, 0L, Integer.MAX_VALUE);
    }

    public static int remainingTicks(long until, long now) {
        return (int) Math.clamp(until - now, 0L, Integer.MAX_VALUE);
    }

    /** Whole seconds shown in messages, rounded up. / 提示中显示的整秒数，向上取整。 */
    public static int ceilSeconds(int ticks) {
        return ticks <= 0 ? 0 : (ticks + 19) / 20;
    }

    public static String messageKey(String suffix) {
        return MESSAGE_PREFIX + suffix;
    }

    private static Identifier noelles(String path) {
        return Identifier.of("noellesroles", path);
    }

    private static Identifier sparkwitch(String path) {
        return Identifier.of(SparkWitch.MOD_ID, path);
    }

    private static Identifier wathe(String path) {
        return Identifier.of("wathe", path);
    }

    private static Set<Identifier> orderedSet(Identifier... ids) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(List.of(ids)));
    }
}
