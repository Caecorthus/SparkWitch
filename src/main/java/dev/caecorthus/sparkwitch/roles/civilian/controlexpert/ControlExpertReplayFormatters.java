package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Wathe replay lines for Control Expert item uses; Wathe drops item uses that have no registered formatter.
 * The Disruptor line names only the user: who or how many were disrupted is never recorded. The Taser line names the
 * target only on a hit. The Shock Device line names only the thrower.
 * 控场专家道具使用的 Wathe 回放文本；未注册格式化器的道具使用会被 Wathe 忽略。
 * 干扰器的回放只记录使用者：从不记录被干扰者的身份或数量。电击枪的回放仅在命中时记录目标。电击装置的回放只记录投掷者。
 */
public final class ControlExpertReplayFormatters {
    static final String DISRUPTOR_KEY = "replay.item_use.sparkwitch.disruptor";
    static final String TASER_HIT_KEY = "replay.item_use.sparkwitch.taser.hit";
    static final String TASER_MISS_KEY = "replay.item_use.sparkwitch.taser.miss";
    static final String SHOCK_DEVICE_KEY = "replay.item_use.sparkwitch.shock_device";
    private static boolean registered;

    private ControlExpertReplayFormatters() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(ControlExpertRules.DISRUPTOR_ID,
                ControlExpertReplayFormatters::formatDisruptor);
        ReplayRegistry.registerItemUseFormatter(ControlExpertRules.TASER_ID,
                ControlExpertReplayFormatters::formatTaser);
        ReplayRegistry.registerItemUseFormatter(ControlExpertRules.SHOCK_DEVICE_ID,
                ControlExpertReplayFormatters::formatShockDevice);
    }

    private static @Nullable Text formatDisruptor(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                                  ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), ReplayGenerator.getPlayerInfoCache(match));
        return Text.translatable(DISRUPTOR_KEY, actor);
    }

    private static @Nullable Text formatTaser(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                              ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        var players = ReplayGenerator.getPlayerInfoCache(match);
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), players);
        if (data.getBoolean("hit") && data.containsUuid("target")) {
            Text target = ReplayGenerator.formatPlayerName(data.getUuid("target"), players);
            return Text.translatable(TASER_HIT_KEY, actor, target);
        }
        return Text.translatable(TASER_MISS_KEY, actor);
    }

    private static @Nullable Text formatShockDevice(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                                    ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), ReplayGenerator.getPlayerInfoCache(match));
        return Text.translatable(SHOCK_DEVICE_KEY, actor);
    }
}
