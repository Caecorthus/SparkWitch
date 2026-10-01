package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

final class FisherReplayFormatters {
    private static boolean registered;

    private FisherReplayFormatters() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(FisherRules.FISHING_ROD_ID, FisherReplayFormatters::format);
    }

    /** The replay stores an enum name, never translated text or private tray contents.
     * 回放存储枚举名，不存储翻译后的文本或托盘的私有内容。 */
    private static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        var data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        FisherCatch caught;
        try {
            caught = FisherCatch.valueOf(data.getString("catch"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), ReplayGenerator.getPlayerInfoCache(match));
        return Text.translatable("replay.item_use.sparkwitch.fishing_rod", actor, Text.translatable(caught.translationKey()));
    }
}
