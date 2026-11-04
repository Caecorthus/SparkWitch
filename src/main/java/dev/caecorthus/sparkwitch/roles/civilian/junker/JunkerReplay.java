package dev.caecorthus.sparkwitch.roles.civilian.junker;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 拾荒者 replay records and their formatter. Stable record contract: Wathe {@code skill_use} events with skill
 * {@link JunkerRules#ROLE_ID} whose data holds {@code action} ({@code "search"} or {@code "found"}), {@code victim}
 * (the corpse owner's UUID) and, for {@code found}, {@code item} (the registry id) and {@code count}. The item is
 * stored by id and rendered through its translation key, so every replay viewer reads it in their own language (the PR
 * stored the server-resolved display string).
 * 拾荒者的回放记录与格式化。稳定记录契约：Wathe 的 {@code skill_use} 事件，技能为 {@link JunkerRules#ROLE_ID}，数据包含
 * {@code action}（{@code "search"} 或 {@code "found"}）、{@code victim}（尸体主人的 UUID），{@code found} 另有
 * {@code item}（注册 id）与 {@code count}。物品按 id 保存并通过翻译键渲染，每位回放查看者都看到自己语言的名称
 * （原 PR 保存的是服务端语言解析后的显示文本）。
 */
public final class JunkerReplay {
    static final String ACTION = "action";
    static final String ACTION_SEARCH = "search";
    static final String ACTION_FOUND = "found";
    static final String VICTIM = "victim";
    static final String ITEM = "item";
    static final String COUNT = "count";
    static final String SEARCH_KEY = "replay.skill.sparkwitch.ragpicker.search";
    static final String SEARCH_UNKNOWN_KEY = "replay.skill.sparkwitch.ragpicker.search_unknown";
    static final String FOUND_KEY = "replay.skill.sparkwitch.ragpicker.found";
    static final String FOUND_UNKNOWN_KEY = "replay.skill.sparkwitch.ragpicker.found_unknown";
    private static boolean registered;

    private JunkerReplay() {
    }

    /** Registers the skill formatter for {@link JunkerRules#ROLE_ID} once. / 注册该职业的技能回放格式化（仅一次）。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(JunkerRules.ROLE_ID, JunkerReplay::format);
    }

    /** The search finished on the corpse of {@code victimUuid}. / 对 victimUuid 的尸体完成了一次搜刮。 */
    public static void recordSearch(ServerPlayerEntity actor, UUID victimUuid) {
        GameRecordManager.recordSkillUse(actor, JunkerRules.ROLE_ID, onlineVictim(actor, victimUuid),
                searchData(victimUuid));
    }

    /**
     * What the search moved into the ragpicker's hotbar. Call it before the stack is inserted, since an insert may
     * merge and empty it; an empty stack records nothing.
     * 本次搜刮转移到拾荒者快捷栏的物品。须在放入背包之前调用，因为放入时可能合并并清空该堆叠；空堆叠不记录。
     */
    public static void recordFound(ServerPlayerEntity actor, UUID victimUuid, ItemStack reward) {
        if (reward.isEmpty()) {
            return;
        }
        GameRecordManager.recordSkillUse(actor, JunkerRules.ROLE_ID, onlineVictim(actor, victimUuid),
                foundData(victimUuid, Registries.ITEM.getId(reward.getItem()), reward.getCount()));
    }

    static NbtCompound searchData(@Nullable UUID victimUuid) {
        NbtCompound data = new NbtCompound();
        data.putString(ACTION, ACTION_SEARCH);
        if (victimUuid != null) {
            data.putUuid(VICTIM, victimUuid);
        }
        return data;
    }

    static NbtCompound foundData(@Nullable UUID victimUuid, Identifier item, int count) {
        NbtCompound data = searchData(victimUuid);
        data.putString(ACTION, ACTION_FOUND);
        data.putString(ITEM, item.toString());
        data.putInt(COUNT, count);
        return data;
    }

    private static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                         ServerWorld world) {
        return format(event, match, JunkerReplay::itemName);
    }

    /**
     * Renders one record, or null for a malformed one (never Wathe's raw-key fallback). {@code itemNames} resolves an
     * item id to its name; tests pass a stub because the item registry needs a bootstrapped game.
     * 渲染一条记录；数据不完整时返回 null（绝不落到 Wathe 的原始键兜底）。{@code itemNames} 把物品 id 解析为名称；
     * 测试传入替身，因为物品注册表需要已引导的游戏环境。
     */
    static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                 Function<Identifier, Text> itemNames) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        Map<UUID, ReplayGenerator.PlayerInfo> cache = ReplayGenerator.getPlayerInfoCache(match);
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), cache);
        Text victim = data.containsUuid(VICTIM) ? ReplayGenerator.formatPlayerName(data.getUuid(VICTIM), cache) : null;
        return switch (data.getString(ACTION)) {
            case ACTION_SEARCH -> victim == null
                    ? Text.translatable(SEARCH_UNKNOWN_KEY, actor)
                    : Text.translatable(SEARCH_KEY, actor, victim);
            case ACTION_FOUND -> {
                Text item = itemText(data, itemNames);
                if (item == null) {
                    yield null;
                }
                yield victim == null
                        ? Text.translatable(FOUND_UNKNOWN_KEY, actor, item)
                        : Text.translatable(FOUND_KEY, actor, item, victim);
            }
            default -> null;
        };
    }

    private static @Nullable Text itemText(NbtCompound data, Function<Identifier, Text> itemNames) {
        String raw = data.getString(ITEM);
        Identifier id = raw.isEmpty() ? null : Identifier.tryParse(raw);
        if (id == null) {
            return null;
        }
        Text name = itemNames.apply(id);
        int count = data.getInt(COUNT);
        return count > 1 ? Text.empty().append(name).append(" ×" + count) : name;
    }

    /**
     * Translatable item name; an id missing from this registry shows as the raw id.
     * 可翻译的物品名；注册表中没有的 id 显示为原始 id。
     */
    private static Text itemName(Identifier id) {
        return Registries.ITEM.getOrEmpty(id)
                .<Text>map(item -> Text.translatable(item.getTranslationKey()))
                .orElseGet(() -> Text.literal(id.toString()));
    }

    private static @Nullable ServerPlayerEntity onlineVictim(ServerPlayerEntity actor, @Nullable UUID victimUuid) {
        return victimUuid == null ? null : actor.getServer().getPlayerManager().getPlayer(victimUuid);
    }
}
