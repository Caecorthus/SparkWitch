package dev.caecorthus.sparkwitch.roles.witch.abysslistener;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Wathe replay lines for Abyss Listener item uses; Wathe drops item uses that have no registered formatter. The
 * Shriek Gun line names the target only on a hit and says whether it was an ally; the flask line names only the
 * thrower. Warden's Shriek uses Wathe's default skill line ({@code replay.skill.sparkwitch.wardens_shriek}).
 * 聆渊者道具使用的 Wathe 回放文本；未注册格式化器的道具使用会被 Wathe 忽略。啸音铳仅在命中时记录目标并区分是否为队友；
 * 孢瓶只记录投掷者。监守之啸使用 Wathe 默认技能文本。
 */
public final class AbyssListenerReplayFormatters {
    static final String GUN_HIT_KEY = "replay.item_use.sparkwitch.shriek_gun.hit";
    static final String GUN_ALLY_KEY = "replay.item_use.sparkwitch.shriek_gun.ally";
    static final String GUN_MISS_KEY = "replay.item_use.sparkwitch.shriek_gun.miss";
    static final String FLASK_KEY = "replay.item_use.sparkwitch.deep_dark_spore_flask";
    private static boolean registered;

    private AbyssListenerReplayFormatters() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(AbyssListenerRules.GUN_ITEM_ID, AbyssListenerReplayFormatters::formatGun);
        ReplayRegistry.registerItemUseFormatter(AbyssListenerRules.FLASK_ITEM_ID,
                AbyssListenerReplayFormatters::formatFlask);
    }

    /** Pure key choice for a gun record. / 啸音铳记录的纯文本键选择。 */
    static String gunKey(boolean hit, boolean ally) {
        if (!hit) {
            return GUN_MISS_KEY;
        }
        return ally ? GUN_ALLY_KEY : GUN_HIT_KEY;
    }

    private static @Nullable Text formatGun(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                           ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        var players = ReplayGenerator.getPlayerInfoCache(match);
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), players);
        boolean hit = data.getBoolean("hit") && data.containsUuid("target");
        String key = gunKey(hit, data.getBoolean("ally"));
        if (!hit) {
            return Text.translatable(key, actor);
        }
        return Text.translatable(key, actor, ReplayGenerator.formatPlayerName(data.getUuid("target"), players));
    }

    private static @Nullable Text formatFlask(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                             ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), ReplayGenerator.getPlayerInfoCache(match));
        return Text.translatable(FLASK_KEY, actor);
    }
}
