package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Time Stealer shop plan: stamp prices per entry id and the native-list rewrite. Entry ids are frozen; the psycho
 * entry keeps Wathe's {@code psycho_mode} id because SparkStrength, SparkTraits and Wathe cooldowns key on it.
 * The rewrite depends only on the entry ids (plus a poison display flag), never on stamps, game time or psycho state:
 * Wathe resolves a purchase by list index against a list the server rebuilds, so both sides must build the same shape.
 * 纯窃时者商店规划：各商品 id 的邮票价格与原生列表改写。商品 id 已冻结；疯魔商品保留 Wathe 的 {@code psycho_mode} id，
 * 因为 SparkStrength、SparkTraits 与 Wathe 冷却都依赖它。改写只取决于商品 id（以及毒药展示标记），从不取决于邮票、
 * 对局时间或疯魔状态：Wathe 按下标解析购买，服务端会重建列表，因此两端必须构建出相同的形状。
 */
public final class TimeStealerShopRules {
    public static final String GRENADE_ENTRY_ID = "sparkwitch_time_stealer_grenade";
    public static final String PSYCHO_ENTRY_ID = "psycho_mode";
    public static final String ADD_TIME_ENTRY_ID = "sparkwitch_time_stealer_add_time";

    /** Native Wathe ids the stamp entries replace. / 被邮票商品替换的 Wathe 原生 id。 */
    static final String NATIVE_GRENADE_ID = "grenade";
    static final String NATIVE_PSYCHO_ID = "psycho_mode";
    /** Native entries the Time Stealer never sells. / 窃时者从不出售的原生商品。 */
    static final Set<String> REMOVED_NATIVE_IDS = Set.of("poison_vial", "scorpion");
    /** Grenade position when Wathe's grenade is missing and no psycho entry anchors it (knife, revolver, grenade). / 缺少原生手雷且无疯魔锚点时的手雷位置。 */
    static final int GRENADE_FALLBACK_INDEX = 2;
    /** Wathe's native psycho_mode cooldown (5 minutes), used only if the native entry is missing. / 原生疯魔冷却（5 分钟），仅在原生商品缺失时使用。 */
    static final int PSYCHO_FALLBACK_COOLDOWN_TICKS = 5 * 60 * 20;

    private TimeStealerShopRules() {
    }

    /** Stamp price of a Time Stealer entry, or 0 for coin-priced and foreign entries. / 窃时者商品的邮票价格；金币商品与其他商品为 0。 */
    public static int stampCost(@Nullable String entryId) {
        if (entryId == null) {
            return 0;
        }
        return switch (entryId) {
            case GRENADE_ENTRY_ID -> TimeStealerRules.STAMP_COST_GRENADE;
            case PSYCHO_ENTRY_ID -> TimeStealerRules.STAMP_COST_PSYCHO;
            case ADD_TIME_ENTRY_ID -> TimeStealerRules.STAMP_COST_ADD_TIME;
            default -> 0;
        };
    }

    /** The three stamp-priced entries. / 三个邮票定价商品。 */
    enum StampEntry {
        GRENADE(GRENADE_ENTRY_ID),
        PSYCHO(PSYCHO_ENTRY_ID),
        ADD_TIME(ADD_TIME_ENTRY_ID);

        private final String id;

        StampEntry(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }

        int cost() {
            return stampCost(id);
        }
    }

    /** Planner input: an entry's id and whether its display item is a poison vial or scorpion. / 规划输入：商品 id 及其展示物品是否为毒药瓶或蝎子。 */
    record NativeEntry(String id, boolean poisonDisplay) {
    }

    /**
     * One list edit, applied in the returned order against the live list: removals first (descending indices), then
     * in-place replacements and insertions whose indices already account for earlier edits.
     * 一次列表编辑，按返回顺序作用于实时列表：先删除（下标递减），再原位替换与插入，其下标已考虑之前的编辑。
     */
    sealed interface Op permits Remove, Replace, Insert {
    }

    /** Remove the entry at {@code index}. / 删除该下标的商品。 */
    record Remove(int index) implements Op {
    }

    /** Replace the native entry at {@code index} (the copy source) with a stamp entry. / 将该下标的原生商品（复制来源）原位替换为邮票商品。 */
    record Replace(int index, StampEntry entry) implements Op {
    }

    /** Insert a new stamp entry at {@code index}. / 在该下标插入新的邮票商品。 */
    record Insert(int index, StampEntry entry) implements Op {
    }

    /**
     * Plans the deny-list rewrite: remove poison vial and scorpion (by id, or by display item), replace the first native
     * grenade and psycho_mode in place, and insert "+1 minute" right after psycho. A missing grenade goes before
     * psycho (or at {@link #GRENADE_FALLBACK_INDEX}); a missing psycho goes right after the grenade. Nothing else moves,
     * and the list is never cleared.
     * 规划黑名单式改写：按 id 或展示物品删除毒药瓶与蝎子，原位替换第一个原生手雷与 psycho_mode，并在疯魔之后插入
     * “+1 分钟”。缺少手雷时放在疯魔之前（或 {@link #GRENADE_FALLBACK_INDEX}）；缺少疯魔时紧跟手雷。其他商品不移动，
     * 列表从不清空。
     */
    static List<Op> plan(List<NativeEntry> entries) {
        List<Op> ops = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (int index = entries.size() - 1; index >= 0; index--) {
            if (removes(entries.get(index))) {
                ops.add(new Remove(index));
            }
        }
        for (NativeEntry entry : entries) {
            if (!removes(entry)) {
                kept.add(entry.id());
            }
        }
        int grenade = kept.indexOf(NATIVE_GRENADE_ID);
        int psycho = kept.indexOf(NATIVE_PSYCHO_ID);
        if (grenade >= 0) {
            ops.add(new Replace(grenade, StampEntry.GRENADE));
        } else {
            grenade = psycho >= 0 ? psycho : Math.min(GRENADE_FALLBACK_INDEX, kept.size());
            ops.add(new Insert(grenade, StampEntry.GRENADE));
            if (psycho >= grenade) {
                psycho++;
            }
        }
        if (psycho >= 0) {
            ops.add(new Replace(psycho, StampEntry.PSYCHO));
        } else {
            psycho = grenade + 1;
            ops.add(new Insert(psycho, StampEntry.PSYCHO));
        }
        ops.add(new Insert(psycho + 1, StampEntry.ADD_TIME));
        return List.copyOf(ops);
    }

    /** Shop fields a stamp entry takes over from the native entry it replaces. / 邮票商品从被替换的原生商品继承的字段。 */
    record CopiedFields(int cooldownTicks, int initialCooldownTicks, int maxStock) {
    }

    /**
     * Copy-three-fields rule: a replacement copies the native {@code cooldownTicks}, {@code initialCooldownTicks} and
     * {@code maxStock} exactly (so the psycho 5-minute cooldown and its {@code psycho_mode} key carry over); without a
     * native source it uses the Wathe defaults, and "+1 minute" never has cooldown or stock (N16).
     * 三字段复制规则：替换项精确复制原生的冷却、初始冷却与库存上限（因此疯魔的 5 分钟冷却及其 {@code psycho_mode}
     * 键得以保留）；没有原生来源时使用 Wathe 默认值，“+1 分钟”永远没有冷却与库存限制（N16）。
     */
    static CopiedFields copiedFields(StampEntry entry, @Nullable CopiedFields nativeFields) {
        if (entry == StampEntry.ADD_TIME) {
            return new CopiedFields(0, 0, -1);
        }
        if (nativeFields != null) {
            return nativeFields;
        }
        return entry == StampEntry.PSYCHO
                ? new CopiedFields(PSYCHO_FALLBACK_COOLDOWN_TICKS, 0, -1)
                : new CopiedFields(0, 0, -1);
    }

    private static boolean removes(NativeEntry entry) {
        return entry.poisonDisplay() || REMOVED_NATIVE_IDS.contains(entry.id());
    }
}
