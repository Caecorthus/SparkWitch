package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Role-owned replay formatters for Clock thefts and stamp purchases (Wathe silently drops unformatted item uses).
 * The theft line is (actor, target); the purchase line is (actor, item name, cost) and reads the frozen
 * {@link TimeStealerRules#REPLAY_ENTRY_KEY} String and {@link TimeStealerRules#REPLAY_COST_KEY} int. Malformed records
 * return {@code null}, which Wathe skips.
 * 窃时者自有的回放格式化器：时钟窃取与邮票购买（Wathe 会静默丢弃未注册格式化器的物品使用记录）。
 * 窃取行为（使用者、目标）；购买行为（使用者、物品名、花费），读取冻结的 {@link TimeStealerRules#REPLAY_ENTRY_KEY}
 * 字符串与 {@link TimeStealerRules#REPLAY_COST_KEY} 整数。数据不完整的记录返回 {@code null}，Wathe 会跳过该行。
 */
public final class TimeStealerReplay {
    static final String CLOCK_KEY = "replay.item_use.sparkwitch.time_stealer_clock";
    static final String PURCHASE_KEY = "replay.item_use.sparkwitch.time_stamp_purchase";
    /** Wathe's own keys for the recording player and the target ({@code GameRecordManager#addEvent}). / Wathe 自身写入的记录玩家与目标键。 */
    static final String WATHE_ACTOR_KEY = "actor";
    static final String WATHE_TARGET_KEY = "target";
    static final String GRENADE_NAME_KEY = "item.wathe.grenade";
    static final String PSYCHO_NAME_KEY = "item.wathe.psycho_mode";
    static final String ADD_TIME_NAME_KEY = "shop.sparkwitch.time_stealer.add_time";

    private static boolean registered;

    private TimeStealerReplay() {
    }

    /** Registers both formatters once; called only from {@code TimeStealerFeatureService}. / 只注册一次；仅由功能服务调用。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(TimeStealerRules.CLOCK_ID, TimeStealerReplay::formatClock);
        ReplayRegistry.registerItemUseFormatter(TimeStealerRules.STAMP_PURCHASE_RECORD_ID,
                TimeStealerReplay::formatPurchase);
    }

    /** (actor, target); both are required. / （使用者、目标）；两者都必须存在。 */
    static @Nullable Text formatClock(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                      @Nullable ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid(WATHE_ACTOR_KEY) || !data.containsUuid(WATHE_TARGET_KEY)) {
            return null;
        }
        Map<UUID, ReplayGenerator.PlayerInfo> players = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(CLOCK_KEY,
                ReplayGenerator.formatPlayerName(data.getUuid(WATHE_ACTOR_KEY), players),
                ReplayGenerator.formatPlayerName(data.getUuid(WATHE_TARGET_KEY), players));
    }

    /** (actor, item name, cost); the entry id must be a non-empty String and the cost a number. / （使用者、物品名、花费）。 */
    static @Nullable Text formatPurchase(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                         @Nullable ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid(WATHE_ACTOR_KEY)
                || !data.contains(TimeStealerRules.REPLAY_ENTRY_KEY, NbtElement.STRING_TYPE)
                || !data.contains(TimeStealerRules.REPLAY_COST_KEY, NbtElement.NUMBER_TYPE)) {
            return null;
        }
        String entryId = data.getString(TimeStealerRules.REPLAY_ENTRY_KEY);
        if (entryId.isEmpty()) {
            return null;
        }
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid(WATHE_ACTOR_KEY),
                ReplayGenerator.getPlayerInfoCache(match));
        String nameKey = entryNameKey(entryId);
        Text item = nameKey != null ? Text.translatable(nameKey) : Text.literal(entryId);
        return Text.translatable(PURCHASE_KEY, actor, item, data.getInt(TimeStealerRules.REPLAY_COST_KEY));
    }

    /**
     * Display-name lang key of a Time Stealer shop entry id, or {@code null} for any other id (shown raw, never parsed
     * as an {@code Identifier}).
     * 窃时者商店商品 id 对应的显示名语言键；其他 id 返回 {@code null}（直接显示原字符串，绝不按 {@code Identifier} 解析）。
     */
    static @Nullable String entryNameKey(@Nullable String entryId) {
        if (entryId == null) {
            return null;
        }
        return switch (entryId) {
            case TimeStealerShopRules.GRENADE_ENTRY_ID -> GRENADE_NAME_KEY;
            case TimeStealerShopRules.PSYCHO_ENTRY_ID -> PSYCHO_NAME_KEY;
            case TimeStealerShopRules.ADD_TIME_ENTRY_ID -> ADD_TIME_NAME_KEY;
            default -> null;
        };
    }
}
