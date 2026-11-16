package dev.caecorthus.sparkwitch.roles.civilian.saint.flash;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Replay line for the throw that {@link HolyFlashItem} records with {@code recordItemUse(thrower, holy_flash, null)}.
 * Wathe drops item-use records with no registered formatter, so until this was registered the line never showed;
 * registering by the Holy Flash item id leaves every other item's formatter untouched.
 * {@link HolyFlashItem} 以 {@code recordItemUse(投掷者, 圣光弹, null)} 记录的投掷回放文本。Wathe 会忽略没有注册格式化器的
 * 物品使用记录，因此注册之前这一行从不显示；按圣光弹物品 id 注册，不影响其他物品的格式化器。
 */
final class HolyFlashReplayFormatter {
    static final String KEY = "replay.item_use.sparkwitch.holy_flash";

    private HolyFlashReplayFormatter() {
    }

    static void register() {
        ReplayRegistry.registerItemUseFormatter(HolyFlashRules.ITEM_ID, HolyFlashReplayFormatter::format);
    }

    /** (thrower); a record without one shows nothing. / （投掷者）；没有投掷者的记录不显示。 */
    @Nullable
    private static Text format(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        return Text.translatable(KEY, ReplayGenerator.formatPlayerName(data.getUuid("actor"),
                ReplayGenerator.getPlayerInfoCache(match)));
    }
}
