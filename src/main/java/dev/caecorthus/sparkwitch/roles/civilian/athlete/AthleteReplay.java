package dev.caecorthus.sparkwitch.roles.civilian.athlete;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Replay of the Athlete's dash: one skill event {@code sparkwitch:athlete_dash} per successful dash only (a refused or
 * blocked request records nothing), shown as "%s used Dash".
 * 运动员冲刺的回放：只有成功的冲刺才记录一个技能事件 {@code sparkwitch:athlete_dash}（被拒绝或受阻的请求什么也不记录），
 * 显示为“%s 使用了冲刺”。
 */
final class AthleteReplay {
    private static boolean registered;

    private AthleteReplay() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(AthleteRules.DASH_SKILL_ID, AthleteReplay::formatDash);
    }

    static void recordDash(ServerPlayerEntity athlete) {
        GameRecordManager.recordSkillUse(athlete, AthleteRules.DASH_SKILL_ID, null, null);
    }

    private static @Nullable Text formatDash(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                             ServerWorld world) {
        if (!event.data().containsUuid("actor")) {
            return null;
        }
        return Text.translatable("replay.skill.sparkwitch.athlete_dash",
                ReplayGenerator.formatPlayerName(event.data().getUuid("actor"),
                        ReplayGenerator.getPlayerInfoCache(match)));
    }
}
