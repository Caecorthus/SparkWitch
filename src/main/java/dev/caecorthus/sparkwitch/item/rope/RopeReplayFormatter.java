package dev.caecorthus.sparkwitch.item.rope;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Replay line for the strangle start that {@link RopeStrangleService} records with {@code recordItemUse(user, rope,
 * victim)}. Wathe drops item-use records with no registered formatter, so until this was registered the line never
 * showed; registering by the rope's item id leaves every other item's formatter untouched.
 * {@link RopeStrangleService} 以 {@code recordItemUse(勒杀者, 绳索, 受害者)} 记录的开始勒杀回放文本。Wathe 会忽略没有注册
 * 格式化器的物品使用记录，因此注册之前这一行从不显示；按绳索物品 id 注册，不影响其他物品的格式化器。
 */
final class RopeReplayFormatter {
    static final String KEY = "replay.item_use.sparkwitch.rope";

    private RopeReplayFormatter() {
    }

    static void register() {
        ReplayRegistry.registerItemUseFormatter(RopeRules.ITEM_ID, RopeReplayFormatter::format);
    }

    /** (strangler, victim); a record missing either side shows nothing. / （勒杀者，受害者）；缺任一方时不显示。 */
    @Nullable
    private static Text format(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        var players = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(KEY, ReplayGenerator.formatPlayerName(data.getUuid("actor"), players),
                ReplayGenerator.formatPlayerName(data.getUuid("target"), players));
    }
}
