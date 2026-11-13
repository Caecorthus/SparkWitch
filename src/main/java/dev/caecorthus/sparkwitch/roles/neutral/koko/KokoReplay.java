package dev.caecorthus.sparkwitch.roles.neutral.koko;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Replay for Koko: skill events under her role id, one action name each and never a server-resolved string, so every
 * viewer reads the line in their own language. A corpse stream stores the dead player's UUID itself (the body's owner
 * may be offline, so Wathe's {@code target} player cannot carry it). The lightstick is recorded for whoever used it.
 * 可可的回放：以其职业 id 记录的技能事件，每条只保存一个动作名，从不保存服务端解析好的字符串，因此每位观看者都以自己的语言
 * 阅读。尸体直播自行保存死者的 UUID（尸体的主人可能已离线，Wathe 的 {@code target} 玩家参数带不了它）。应援棒记录在实际
 * 使用者名下。
 */
final class KokoReplay {
    private static final String ACTION = "action";
    private static final String CORPSE = "corpse";
    private static final String STREAM_START = "stream_start";
    private static final String STREAM_END = "stream_end";
    private static final String CORPSE_STREAM = "corpse_stream";
    private static final String LIGHTSTICK = "lightstick";
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

    static void recordStreamStart(ServerPlayerEntity koko) {
        record(koko, STREAM_START, null);
    }

    static void recordStreamEnd(ServerPlayerEntity koko) {
        record(koko, STREAM_END, null);
    }

    static void recordCorpseStream(ServerPlayerEntity koko, @Nullable UUID corpseOwner) {
        record(koko, CORPSE_STREAM, corpseOwner);
    }

    static void recordLightstick(ServerPlayerEntity user) {
        record(user, LIGHTSTICK, null);
    }

    private static void record(ServerPlayerEntity actor, String action, @Nullable UUID corpseOwner) {
        NbtCompound data = new NbtCompound();
        data.putString(ACTION, action);
        if (corpseOwner != null) {
            data.putUuid(CORPSE, corpseOwner);
        }
        GameRecordManager.recordSkillUse(actor, KokoRules.ROLE_ID, null, data);
    }

    private static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                         ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        var players = ReplayGenerator.getPlayerInfoCache(match);
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), players);
        String action = data.getString(ACTION);
        return switch (action) {
            case CORPSE_STREAM -> data.containsUuid(CORPSE)
                    ? Text.translatable(KEY_PREFIX + "corpse_stream", actor,
                    ReplayGenerator.formatPlayerName(data.getUuid(CORPSE), players))
                    : Text.translatable(KEY_PREFIX + "corpse_stream_unknown", actor);
            case STREAM_START, STREAM_END, LIGHTSTICK -> Text.translatable(KEY_PREFIX + action, actor);
            default -> null;
        };
    }
}
