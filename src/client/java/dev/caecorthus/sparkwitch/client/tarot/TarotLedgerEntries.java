package dev.caecorthus.sparkwitch.client.tarot;

import dev.caecorthus.sparkwitch.client.text.WitchRoleDisplayTexts;
import dev.caecorthus.sparkwitch.roles.civilian.tarotreader.TarotReaderRules;
import dev.caecorthus.sparkwitch.util.RoleDisplayTextRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Builds, groups, sorts and filters the Tarot Reader selector's rows. Leak guard: every row comes from static
 * registry data (role ids, names, colours, registered base factions, {@code WatheRoles.SPECIAL_ROLES}) or from the
 * selector packet's player list. Nothing here reads round state: no {@code shouldAppear}, no assignments, no dead
 * list, no live alignment.
 * 构建、分组、排序并筛选塔罗牌师选择界面的条目。防泄露约束：所有条目仅来自静态注册数据（职业 ID、名称、颜色、
 * 注册的基础阵营、{@code WatheRoles.SPECIAL_ROLES}）或选择界面数据包中的玩家列表。此处从不读取本局状态：
 * 不调用 {@code shouldAppear}，不读取职业分配、死亡名单或实时阵营。
 */
public final class TarotLedgerEntries {
    private static final String CHINESE_LANGUAGE_CODE = "zh_cn";
    private static final String NAMESPACE_SEPARATOR = ":";
    private static final int NO_COLOR = -1;

    private TarotLedgerEntries() {
    }

    /**
     * {@code WatheRoles.ROLES} minus NO_ROLE and DISCOVERY_CIVILIAN, deduped by id (first wins), in registry order.
     * {@link #group} sorts them.
     * {@code WatheRoles.ROLES} 去掉 NO_ROLE 与 DISCOVERY_CIVILIAN 并按 ID 去重（保留首个），保持注册顺序，由
     * {@link #group} 负责排序。
     */
    public static List<Entry> identity() {
        Set<String> specialTargets = new HashSet<>();
        for (Role role : WatheRoles.SPECIAL_ROLES) {
            specialTargets.add(role.identifier().toString());
        }
        List<Entry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Role role : WatheRoles.ROLES) {
            if (role == WatheRoles.NO_ROLE || role == WatheRoles.DISCOVERY_CIVILIAN) {
                continue;
            }
            String target = role.identifier().toString();
            if (!seen.add(target)) {
                continue;
            }
            Section section = specialTargets.contains(target)
                    ? Section.SPECIAL
                    : section(TarotReaderRules.staticBucket(role));
            result.add(new Entry(
                    target,
                    WitchRoleDisplayTexts.roleName(RoleDisplayTextRules.roleTranslationKey(role)).getString(),
                    target,
                    role.color() & 0xFFFFFF,
                    section,
                    false
            ));
        }
        return List.copyOf(result);
    }

    /**
     * Exactly the packet's (uuid, name) pairs, dead players and the reader included; extra unpaired values are
     * ignored. {@code self} is the local player's UUID and only sets the self tag.
     * 严格对应数据包中的 (uuid, 名称) 配对，包括已死亡玩家与占卜者本人；多余的未配对值被忽略。{@code self} 为本地
     * 玩家 UUID，仅用于标记"你"。
     */
    public static List<Entry> survival(List<UUID> ids, List<String> names, @Nullable UUID self) {
        int count = Math.min(ids.size(), names.size());
        List<Entry> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            UUID id = ids.get(index);
            String name = names.get(index);
            result.add(new Entry(id.toString(), name, name, NO_COLOR, Section.PLAYERS, id.equals(self)));
        }
        return List.copyOf(result);
    }

    /**
     * Groups rows by section in enum order, sorted by {@code labelOrder} then target; empty sections are dropped.
     * The players section always sorts by {@code CASE_INSENSITIVE_ORDER}, then target, whatever {@code labelOrder}
     * is, as the survival selector always has.
     * 按枚举顺序分组，组内先按 {@code labelOrder} 再按 target 排序，并去掉空分组。无论 {@code labelOrder} 为何，玩家
     * 分组始终按 {@code CASE_INSENSITIVE_ORDER} 再按 target 排序，与存活选择界面一贯的顺序一致。
     */
    public static List<Group> group(List<Entry> entries, Comparator<String> labelOrder) {
        Map<Section, List<Entry>> bySection = new EnumMap<>(Section.class);
        for (Entry entry : entries) {
            bySection.computeIfAbsent(entry.section(), ignored -> new ArrayList<>()).add(entry);
        }
        List<Group> result = new ArrayList<>(bySection.size());
        for (Map.Entry<Section, List<Entry>> bucket : bySection.entrySet()) {
            Comparator<String> order = bucket.getKey() == Section.PLAYERS
                    ? String.CASE_INSENSITIVE_ORDER
                    : labelOrder;
            List<Entry> sorted = bucket.getValue();
            sorted.sort(Comparator.comparing(Entry::label, order).thenComparing(Entry::target));
            result.add(new Group(bucket.getKey(), sorted));
        }
        return List.copyOf(result);
    }

    /**
     * Case-insensitive substring match over the label and the search key, with the query trimmed. A role id matches
     * on its path; its namespace joins the match only when the query itself contains ':', so "witch" does not list
     * every {@code sparkwitch:} role while "noellesroles:assassin" still works. Blank query returns {@code groups};
     * emptied groups are dropped and order is preserved.
     * 对名称与搜索键做不区分大小写的子串匹配（查询先去除首尾空白）。职业 ID 只匹配路径部分；仅当查询本身含 ':'
     * 时才连同命名空间一起匹配，因此输入 "witch" 不会列出所有 {@code sparkwitch:} 职业，而 "noellesroles:assassin"
     * 仍可命中。空查询原样返回 {@code groups}；筛空的分组被去掉，顺序保持不变。
     */
    public static List<Group> filter(List<Group> groups, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return groups;
        }
        List<Group> result = new ArrayList<>(groups.size());
        for (Group group : groups) {
            List<Entry> matches = new ArrayList<>();
            for (Entry entry : group.entries()) {
                if (matches(entry, needle)) {
                    matches.add(entry);
                }
            }
            if (!matches.isEmpty()) {
                result.add(new Group(group.section(), matches));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Label order for the client language: pinyin for zh_cn, otherwise the root collator at PRIMARY strength. Never
     * throws; falls back to {@code CASE_INSENSITIVE_ORDER} when collation data is unavailable.
     * 按客户端语言排序名称：zh_cn 使用拼音顺序，其余语言使用 ROOT 排序器的 PRIMARY 强度。不会抛出异常；排序数据
     * 不可用时回退到 {@code CASE_INSENSITIVE_ORDER}。
     */
    public static Comparator<String> labelOrder(@Nullable String languageCode) {
        try {
            Collator collator;
            if (CHINESE_LANGUAGE_CODE.equalsIgnoreCase(languageCode)) {
                collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
            } else {
                collator = Collator.getInstance(Locale.ROOT);
                collator.setStrength(Collator.PRIMARY);
            }
            return collator::compare;
        } catch (RuntimeException exception) {
            return String.CASE_INSENSITIVE_ORDER;
        }
    }

    /**
     * Section of a non-special role from its static bucket; a null bucket (faction NONE) goes to SPECIAL so no role
     * is ever dropped.
     * 非特殊职业按静态阵营桶归入分组；阵营桶为 null（阵营 NONE）时归入特殊，确保不会丢失任何职业。
     */
    static Section section(@Nullable TarotReaderRules.FactionBucket bucket) {
        if (bucket == null) {
            return Section.SPECIAL;
        }
        return switch (bucket) {
            case CIVILIAN -> Section.CIVILIAN;
            case KILLER -> Section.KILLER;
            case NEUTRAL -> Section.NEUTRAL;
            case WITCH -> Section.WITCH;
        };
    }

    private static boolean matches(Entry entry, String needle) {
        return entry.label().toLowerCase(Locale.ROOT).contains(needle)
                || searchScope(entry.searchKey(), needle).toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String searchScope(String searchKey, String needle) {
        int separator = searchKey.indexOf(NAMESPACE_SEPARATOR);
        if (separator < 0 || needle.contains(NAMESPACE_SEPARATOR)) {
            return searchKey;
        }
        return searchKey.substring(separator + 1);
    }

    public enum Section {
        CIVILIAN,
        KILLER,
        NEUTRAL,
        WITCH,
        SPECIAL,
        PLAYERS
    }

    /**
     * One selector row. {@code target} is the exact string the C2S submit packet and the reading log carry (role id
     * or player UUID); {@code rgb} is the role colour without alpha, or -1 for players.
     * 选择界面的一行。{@code target} 与 C2S 提交包及占卜记录携带的字符串完全一致（职业 ID 或玩家 UUID）；
     * {@code rgb} 为不含 alpha 的职业颜色，玩家行为 -1。
     */
    public record Entry(String target, String label, String searchKey, int rgb, Section section, boolean self) {
    }

    public record Group(Section section, List<Entry> entries) {
        public Group {
            entries = List.copyOf(entries);
        }
    }
}
