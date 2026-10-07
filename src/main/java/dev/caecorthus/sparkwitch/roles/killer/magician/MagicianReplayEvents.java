package dev.caecorthus.sparkwitch.roles.killer.magician;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** 魔术师回放全局事件 ID 与格式化注册。 */
public final class MagicianReplayEvents {
    public static final Identifier RECORDING_STARTED = id("recording_started");
    public static final Identifier RECORDING_FINISHED = id("recording_finished");
    public static final Identifier RECORDING_STOPPED_EARLY = id("recording_stopped_early");
    public static final Identifier PLAYBACK_STARTED = id("playback_started");
    public static final Identifier PLAYBACK_FINISHED = id("playback_finished");
    public static final Identifier PLAYBACK_STOPPED_EARLY = id("playback_stopped_early");
    public static final Identifier PLAYBACK_FORCED_END = id("playback_forced_end");
    private MagicianReplayEvents() {}
    private static Identifier id(String path) { return SparkWitch.id("magician_" + path); }
    public static void register() {
        ReplayRegistry.registerGlobalEventFormatter(RECORDING_STARTED, MagicianReplayEvents::formatRecordingStarted);
        ReplayRegistry.registerGlobalEventFormatter(RECORDING_FINISHED, MagicianReplayEvents::formatRecordingFinished);
        ReplayRegistry.registerGlobalEventFormatter(RECORDING_STOPPED_EARLY, MagicianReplayEvents::formatRecordingStoppedEarly);
        ReplayRegistry.registerGlobalEventFormatter(PLAYBACK_STARTED, MagicianReplayEvents::formatPlaybackStarted);
        ReplayRegistry.registerGlobalEventFormatter(PLAYBACK_FINISHED, MagicianReplayEvents::formatPlaybackFinished);
        ReplayRegistry.registerGlobalEventFormatter(PLAYBACK_STOPPED_EARLY, MagicianReplayEvents::formatPlaybackStoppedEarly);
        ReplayRegistry.registerGlobalEventFormatter(PLAYBACK_FORCED_END, MagicianReplayEvents::formatPlaybackForcedEnd);
    }

    @Nullable
    private static Text formatRecordingStarted(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        Text actor = actorText(event, match);
        return actor == null ? null : Text.translatable(
                "replay.global.sparkwitch.magician_recording_started", actor);
    }

    @Nullable
    private static Text formatRecordingFinished(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        Text actor = actorText(event, match);
        return actor == null ? null : Text.translatable(
                "replay.global.sparkwitch.magician_recording_finished", actor);
    }

    @Nullable
    private static Text formatRecordingStoppedEarly(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        Text actor = actorText(event, match);
        return actor == null ? null : Text.translatable(
                "replay.global.sparkwitch.magician_recording_stopped_early", actor);
    }

    @Nullable
    private static Text formatPlaybackStarted(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        Text actor = actorText(event, match);
        Text disguise = playerFromUuidOrName(event, match, "disguise_player", "disguise_name");
        if (actor == null || disguise == null) {
            return null;
        }
        return Text.translatable(
                "replay.global.sparkwitch.magician_playback_started", actor, disguise);
    }

    @Nullable
    private static Text formatPlaybackFinished(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        Text actor = actorText(event, match);
        return actor == null ? null : Text.translatable(
                "replay.global.sparkwitch.magician_playback_finished", actor);
    }

    @Nullable
    private static Text formatPlaybackStoppedEarly(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        Text actor = actorText(event, match);
        return actor == null ? null : Text.translatable(
                "replay.global.sparkwitch.magician_playback_stopped_early", actor);
    }

    @Nullable
    private static Text formatPlaybackForcedEnd(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        Text actor = actorText(event, match);
        Text disguise = playerFromUuidOrName(event, match, "disguise_player", "disguise_name");
        Text attacker = playerFromUuidOrName(event, match, "attacker_player", "attacker_name");
        Text weapon = event.data().contains("weapon_name")
                ? weaponNameText(event.data().getString("weapon_name"))
                : Text.translatable("replay.item.unknown");
        if (actor == null || disguise == null || attacker == null) {
            return null;
        }
        return Text.translatable(
                "replay.global.sparkwitch.magician_playback_forced_end",
                actor,
                disguise,
                attacker,
                weapon
        );
    }

    private static @Nullable Text actorText(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match
    ) {
        if (!event.data().containsUuid("actor")) {
            return null;
        }
        return ReplayGenerator.formatPlayerName(
                event.data().getUuid("actor"),
                ReplayGenerator.getPlayerInfoCache(match)
        );
    }

    /** Prefer the match snapshot, then the stable name saved when the event happened. */
    private static @Nullable Text playerFromUuidOrName(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            String uuidKey,
            String nameKey
    ) {
        NbtCompound data = event.data();
        if (data.containsUuid(uuidKey)) {
            var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
            if (playerInfo.containsKey(data.getUuid(uuidKey))) {
                return ReplayGenerator.formatPlayerName(data.getUuid(uuidKey), playerInfo);
            }
        }
        if (data.contains(nameKey)) {
            return Text.literal(data.getString(nameKey));
        }
        if (data.containsUuid(uuidKey)) {
            return ReplayGenerator.formatPlayerName(
                    data.getUuid(uuidKey),
                    ReplayGenerator.getPlayerInfoCache(match)
            );
        }
        return null;
    }

    private static Text weaponNameText(String weaponName) {
        if (weaponName.startsWith("item.")
                || weaponName.startsWith("block.")
                || weaponName.startsWith("entity.")
                || weaponName.startsWith("replay.")) {
            return Text.translatable(weaponName);
        }
        return Text.literal(weaponName);
    }
}
