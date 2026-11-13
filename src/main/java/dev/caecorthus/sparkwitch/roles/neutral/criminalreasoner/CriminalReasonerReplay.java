package dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Replay of one reasoning attempt: "%s reasoned that %s was killed by %s" on a hit, the failure line on a miss.
 * 一次推理的回放：命中时“%s 推断 %s 被 %s 所杀”，失手时用失败文案。
 */
final class CriminalReasonerReplay {
    private static boolean registered;

    private CriminalReasonerReplay() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(CriminalReasonerRules.ROLE_ID,
                CriminalReasonerReplay::format);
    }

    private static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                         net.minecraft.server.world.ServerWorld world) {
        var data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("victim") || !data.containsUuid("suspect")) {
            return null;
        }
        var cache = ReplayGenerator.getPlayerInfoCache(match);
        Identifier ignored = CriminalReasonerRules.ROLE_ID;
        return Text.translatable(data.getBoolean("success")
                        ? "replay.skill.sparkwitch.criminal_reasoner.success"
                        : "replay.skill.sparkwitch.criminal_reasoner.fail",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), cache),
                ReplayGenerator.formatPlayerName(data.getUuid("victim"), cache),
                ReplayGenerator.formatPlayerName(data.getUuid("suspect"), cache));
    }
}
