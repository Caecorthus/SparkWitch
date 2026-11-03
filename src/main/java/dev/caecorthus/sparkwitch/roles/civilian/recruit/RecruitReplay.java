package dev.caecorthus.sparkwitch.roles.civilian.recruit;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Replay for the Recruit's first-task reward: one global event naming the player who received their knife.
 * 新兵首个任务奖励的回放：记录一个全局事件，写明拿到自己那把刀的新兵。
 */
final class RecruitReplay {
    /** {@code sparkwitch:recruit_knife_granted}. / 新兵拿到刀的事件 id。 */
    static final Identifier KNIFE_GRANTED = SparkWitch.id("recruit_knife_granted");

    private static boolean registered;

    private RecruitReplay() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerGlobalEventFormatter(KNIFE_GRANTED, RecruitReplay::formatKnifeGranted);
    }

    static void recordKnifeGranted(ServerPlayerEntity recruit) {
        GameRecordManager.recordGlobalEvent(recruit.getServerWorld(), KNIFE_GRANTED, recruit, null);
    }

    private static @Nullable Text formatKnifeGranted(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                                     ServerWorld world) {
        if (!event.data().containsUuid("actor")) {
            return null;
        }
        return Text.translatable("replay.global.sparkwitch.recruit_knife_granted",
                ReplayGenerator.formatPlayerName(event.data().getUuid("actor"),
                        ReplayGenerator.getPlayerInfoCache(match)));
    }
}
