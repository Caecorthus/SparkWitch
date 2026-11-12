package dev.caecorthus.sparkwitch.roles.neutral.koko;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Koko's replay lines: stream start/end, corpse stream, lightstick. 可可的回放：开播、结束、尸体直播、应援棒。 */
final class KokoReplay {
    private static final String KEY_PREFIX = "replay.skill.sparkwitch.koko.";
    private static boolean registered;

    private KokoReplay() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(KokoRules.ROLE_ID, KokoReplay::format);
    }

    static void record(ServerPlayerEntity koko, String action) {
        NbtCompound extra = new NbtCompound();
        extra.putString("action", action);
        GameRecordManager.recordSkillUse(koko, KokoRules.ROLE_ID, null, extra);
    }

    @Nullable
    private static Text format(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        NbtCompound data = event.data();
        UUID actor = data.containsUuid("actor") ? data.getUuid("actor") : null;
        if (actor == null) {
            return null;
        }
        Text actorText = ReplayGenerator.formatPlayerName(actor, ReplayGenerator.getPlayerInfoCache(match));
        return switch (data.getString("action")) {
            case "stream_start" -> Text.translatable(KEY_PREFIX + "stream_start", actorText);
            case "end_stream" -> Text.translatable(KEY_PREFIX + "end_stream", actorText);
            case "corpse_stream" -> Text.translatable(KEY_PREFIX + "corpse_stream", actorText);
            case "lightstick" -> Text.translatable(KEY_PREFIX + "lightstick", actorText);
            default -> null;
        };
    }
}
