package dev.caecorthus.sparkwitch.item.m1903a4;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Replay lines of the M1903A4, registered by item id so every other item's formatter is untouched: a reload (the count
 * is the stack size after the round went in), a shot that hit a player ("shot %s", recorded before the kill; Wathe
 * records the death itself, so a shielded or vetoed hit never reads as a kill), and a shot that hit no one (a miss, or
 * a round a crow, puppet or device took).
 * M1903A4 的回放文本，按物品 id 注册，不影响其他物品的格式器：装填（数字为装入这一发之后的弹栈数量）、命中玩家的射击
 * （“射中 %s”，在击杀之前记录；死亡由 Wathe 自行记录，因此被护盾或否决挡下的命中不会显示为击杀），以及没有打中人的射击
 * （未命中，或子弹被乌鸦、皮套或设备吃下）。
 */
public final class M1903A4Replay {
    static final String RELOAD_KEY = "replay.item_use.sparkwitch.m1903a4.reload";
    static final String SHOT_KEY = "replay.item_use.sparkwitch.m1903a4.shot";
    static final String MISS_KEY = "replay.item_use.sparkwitch.m1903a4.miss";
    private static boolean registered;

    private M1903A4Replay() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(SparkWitchItems.M1903A4_ID, M1903A4Replay::format);
    }

    @Nullable
    private static Text format(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        var players = ReplayGenerator.getPlayerInfoCache(match);
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), players);
        String key = M1903A4Rules.replayKey(data.getString(M1903A4Rules.RECORD_ACTION),
                data.getBoolean(M1903A4Rules.RECORD_HIT), data.containsUuid("target"));
        return switch (key) {
            case RELOAD_KEY -> Text.translatable(RELOAD_KEY, actor, data.getInt(M1903A4Rules.RECORD_LOADED_ROUNDS));
            case SHOT_KEY -> Text.translatable(SHOT_KEY, actor,
                    ReplayGenerator.formatPlayerName(data.getUuid("target"), players));
            default -> Text.translatable(MISS_KEY, actor);
        };
    }
}
