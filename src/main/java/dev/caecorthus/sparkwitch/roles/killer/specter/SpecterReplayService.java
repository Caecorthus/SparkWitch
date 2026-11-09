package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Replay lines for the ghost-trigger lifecycle and the peachwood sword hit. The otherworld skill formatter lives in
 * {@link SpecterSkillService}; the mark and shriek were already registered there.
 * 鬼魂触发器放置/触发与桃木剑命中的回放文案。里世界技能格式化在 {@link SpecterSkillService}；标记与尖啸此前已注册。
 */
public final class SpecterReplayService {
    private static boolean registered;

    private SpecterReplayService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(SparkWitchItems.GHOST_TRIGGER_ID, SpecterReplayService::formatTriggerPlace);
        ReplayRegistry.registerGlobalEventFormatter(GhostTriggerEntity.EVENT_TRIGGERED, SpecterReplayService::formatTriggered);
        ReplayRegistry.registerItemUseFormatter(SparkWitchItems.PEACHWOOD_SWORD_ID, SpecterReplayService::formatPeachwoodHit);
    }

    private static @Nullable Text formatTriggerPlace(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        return Text.translatable(
                "replay.item_use.sparkwitch.ghost_trigger.place",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), ReplayGenerator.getPlayerInfoCache(match))
        );
    }

    private static @Nullable Text formatTriggered(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("target")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        Text target = ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo);
        if (!data.containsUuid("actor")) {
            return Text.translatable(
                    "replay.global.sparkwitch.ghost_trigger_triggered.no_owner",
                    target
            );
        }
        return Text.translatable(
                "replay.global.sparkwitch.ghost_trigger_triggered",
                target,
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo)
        );
    }

    private static @Nullable Text formatPeachwoodHit(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(
                "replay.item_use.sparkwitch.peachwood_sword.hit",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo),
                ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo)
        );
    }
}
