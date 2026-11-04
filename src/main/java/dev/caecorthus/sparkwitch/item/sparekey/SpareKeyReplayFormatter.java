package dev.caecorthus.sparkwitch.item.sparekey;

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
 * Replay line for the Spare Key's {@code recordItemUse}. Wathe drops item-use records with no registered formatter;
 * registering by this item id leaves every other item's formatter untouched.
 * 备用钥匙 {@code recordItemUse} 的回放文本。Wathe 会忽略没有注册格式器的物品使用记录；按本物品 id 注册不影响其他物品的格式器。
 */
final class SpareKeyReplayFormatter {

    private SpareKeyReplayFormatter() {
    }

    static void register() {
        ReplayRegistry.registerItemUseFormatter(SpareKeyRules.ITEM_ID, SpareKeyReplayFormatter::format);
    }

    @Nullable
    private static Text format(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        UUID actor = actor(event.data());
        return actor == null ? null : Text.translatable("replay.item_use.sparkwitch.spare_key",
                ReplayGenerator.formatPlayerName(actor, ReplayGenerator.getPlayerInfoCache(match)));
    }

    @Nullable
    private static UUID actor(NbtCompound data) {
        return data.containsUuid("actor") ? data.getUuid("actor") : null;
    }
}
