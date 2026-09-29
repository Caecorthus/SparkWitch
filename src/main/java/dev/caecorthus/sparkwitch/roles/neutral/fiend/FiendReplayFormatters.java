package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Wathe replay lines for the Fiend Moment global events. Every record carries the Fiend UUID under {@link #FIEND_KEY}
 * (the end event may be recorded after the Fiend left, when Wathe cannot add its own actor key). Malformed records
 * return {@code null}, which Wathe skips.
 * 魔人时刻全局事件的 Wathe 回放文本。每条记录都在 {@link #FIEND_KEY} 下携带魔人 UUID（结束事件可能在魔人离开后记录，
 * 此时 Wathe 无法写入自身的 actor 键）。数据不完整的记录返回 {@code null}，Wathe 会跳过该行。
 */
final class FiendReplayFormatters {
    static final String FIEND_KEY = "fiend";
    static final String REASON_KEY = "reason";
    static final String DURATION_KEY = "duration_ticks";
    static final String REASON_DIED = "died";
    static final String REASON_ENDED = "ended";
    static final String START_KEY = "replay.global.sparkwitch.fiend_moment_start";
    static final String END_DIED_KEY = "replay.global.sparkwitch.fiend_moment_end";
    static final String END_ENDED_KEY = "replay.global.sparkwitch.fiend_moment_end.ended";
    private static boolean registered;

    private FiendReplayFormatters() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerGlobalEventFormatter(FiendMomentRules.START_EVENT_ID, FiendReplayFormatters::formatStart);
        ReplayRegistry.registerGlobalEventFormatter(FiendMomentRules.END_EVENT_ID, FiendReplayFormatters::formatEnd);
    }

    static String endReasonValue(FiendMomentRules.EndReason reason) {
        return reason == FiendMomentRules.EndReason.DIED ? REASON_DIED : REASON_ENDED;
    }

    private static @Nullable Text formatStart(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                              @Nullable ServerWorld world) {
        return translateFiend(START_KEY, event.data(), match);
    }

    private static @Nullable Text formatEnd(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                            @Nullable ServerWorld world) {
        NbtCompound data = event.data();
        String key = switch (data.getString(REASON_KEY)) {
            case REASON_DIED -> END_DIED_KEY;
            case REASON_ENDED -> END_ENDED_KEY;
            default -> null;
        };
        return key == null ? null : translateFiend(key, data, match);
    }

    private static @Nullable Text translateFiend(String key, NbtCompound data, GameRecordManager.MatchRecord match) {
        if (!data.containsUuid(FIEND_KEY)) {
            return null;
        }
        Text fiend = ReplayGenerator.formatPlayerName(data.getUuid(FIEND_KEY), ReplayGenerator.getPlayerInfoCache(match));
        return Text.translatable(key, fiend);
    }
}
