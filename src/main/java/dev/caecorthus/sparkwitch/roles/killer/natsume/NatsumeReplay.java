package dev.caecorthus.sparkwitch.roles.killer.natsume;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Replay for 夏目安安: the skill events {@link NatsumeService} records under her role id (hypnosis landed, or the
 * target was immune). Ported from the author's fork, where it lived in a shared {@code SparkWitchReplayFormatters}.
 * 夏目安安的回放：{@link NatsumeService} 以其职业 id 记录的技能事件（催眠生效，或目标免疫）。移植自作者 fork，原位于共享的
 * {@code SparkWitchReplayFormatters}。
 */
public final class NatsumeReplay {
    private static boolean registered;

    private NatsumeReplay() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(NatsumeRules.ROLE_ID, NatsumeReplay::format);
    }

    private static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                         ServerWorld world) {
        NbtCompound data = event.data();
        UUID actor = data.containsUuid("actor") ? data.getUuid("actor") : null;
        UUID target = data.containsUuid("target") ? data.getUuid("target") : null;
        if (actor == null || target == null) {
            return null;
        }
        Map<UUID, ReplayGenerator.PlayerInfo> cache = ReplayGenerator.getPlayerInfoCache(match);
        Text actorText = ReplayGenerator.formatPlayerName(actor, cache);
        Text targetText = ReplayGenerator.formatPlayerName(target, cache);
        if ("immune".equals(data.getString("action"))) {
            return Text.translatable("replay.skill.sparkwitch.natsume.immune", targetText, actorText);
        }
        return Text.translatable("replay.skill.sparkwitch.natsume.effect", actorText, targetText);
    }
}
