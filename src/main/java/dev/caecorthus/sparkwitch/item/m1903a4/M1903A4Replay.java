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

import java.util.UUID;

/**
 * Replay line for an M1903A4 reload, registered by item id so every other item's formatter is untouched. The count is
 * the stack size after the round went in.
 * M1903A4 装填的回放文本，按物品 id 注册，不影响其他物品的格式器。数字为装入这一发之后的弹栈数量。
 */
public final class M1903A4Replay {
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
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), ReplayGenerator.getPlayerInfoCache(match));
        if ("reload".equals(data.getString("action"))) {
            return Text.translatable(
                    "replay.item_use.sparkwitch.m1903a4.reload", actor, data.getInt("loaded_rounds"));
        }
        if (data.containsUuid("target")) {
            return Text.translatable(
                    "replay.item_use.sparkwitch.m1903a4.kill",
                    actor,
                    ReplayGenerator.formatPlayerName(data.getUuid("target"), ReplayGenerator.getPlayerInfoCache(match))
            );
        }
        return null;
    }
}
