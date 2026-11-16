package dev.caecorthus.sparkwitch.roles.killer.hiro.witchification;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Wathe replay line for 魔女化, one global event per witchification (Fiend Moment precedent). The record carries Hiro's
 * UUID and the kill count; a malformed record returns {@code null}, which Wathe skips. The line exists only in the
 * post-round replay: nobody is told during the round.
 * 魔女化的 Wathe 回放文本，每次魔女化记录一条全局事件（沿用魔人时刻的做法）。记录携带希罗的 UUID 与击杀数；数据不完整时返回
 * {@code null}，Wathe 会跳过该行。该行只出现在局后回放中：对局期间不通知任何人。
 */
final class HiroWitchificationReplay {
    static final String HIRO_KEY = "hiro";
    static final String KILLS_KEY = "kills";
    static final String LINE_KEY = "replay.global.sparkwitch.nikaido_hiro_witchified";
    private static boolean registered;

    private HiroWitchificationReplay() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerGlobalEventFormatter(HiroWitchificationRules.REPLAY_EVENT_ID,
                HiroWitchificationReplay::format);
    }

    static void record(ServerWorld world, ServerPlayerEntity hiro, int kills) {
        NbtCompound data = new NbtCompound();
        data.putUuid(HIRO_KEY, hiro.getUuid());
        data.putInt(KILLS_KEY, kills);
        GameRecordManager.recordGlobalEvent(world, HiroWitchificationRules.REPLAY_EVENT_ID, hiro, data);
    }

    private static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                         @Nullable ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid(HIRO_KEY)) {
            return null;
        }
        Text hiro = ReplayGenerator.formatPlayerName(data.getUuid(HIRO_KEY), ReplayGenerator.getPlayerInfoCache(match));
        return Text.translatable(LINE_KEY, hiro, data.getInt(KILLS_KEY));
    }
}
