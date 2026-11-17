package dev.caecorthus.sparkwitch.item.defense;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Replay line for a completed defence-vial drink, so the round summary shows who shielded themselves.
 * 防御药剂喝完后的回放行，让回合总结能显示谁给自己上了护盾。
 */
public final class DefenseVialReplay {
    private static boolean registered;

    private DefenseVialReplay() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(SparkWitch.id("defense_vial"), DefenseVialReplay::format);
    }

    private static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        if (!event.data().containsUuid("actor")) {
            return null;
        }
        return Text.translatable("replay.item.sparkwitch.defense_vial.drink",
                ReplayGenerator.formatPlayerName(event.data().getUuid("actor"),
                        ReplayGenerator.getPlayerInfoCache(match)));
    }
}
