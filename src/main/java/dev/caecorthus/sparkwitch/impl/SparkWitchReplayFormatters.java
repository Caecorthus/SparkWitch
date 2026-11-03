package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.roles.civilian.discoverer.DiscovererRules;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerRules;
import dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner.CriminalReasonerRules;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayEventFormatter;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Replay lines for the roles SparkWitch added. Registering per skill id keeps Wathe's generic formatter for everyone
 * else; each line needs more than the generic (actor, target) pair, hence the dedicated formatters.
 * 为 SparkWitch 新增的身份提供回放文本。按技能 id 注册不会顶掉 Wathe 对其它技能的通用格式器；
 * 这几行需要比通用格式器的 (执行者, 目标) 更多的参数，所以各自单独实现。
 */
public final class SparkWitchReplayFormatters {

    private SparkWitchReplayFormatters() {
    }

    public static void register() {
        ReplayRegistry.registerSkillFormatter(JunkerRules.ROLE_ID, SparkWitchReplayFormatters::junker);
        ReplayRegistry.registerSkillFormatter(CriminalReasonerRules.ROLE_ID, SparkWitchReplayFormatters::reasoner);
        ReplayRegistry.registerSkillFormatter(DiscovererRules.ROLE_ID, SparkWitchReplayFormatters::discoverer);
        ReplayRegistry.registerSkillFormatter(
                dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules.ROLE_ID,
                SparkWitchReplayFormatters::sherry);
        ReplayRegistry.registerSkillFormatter(
                dev.caecorthus.sparkwitch.roles.civilian.noa.NoaRules.ROLE_ID,
                SparkWitchReplayFormatters::noa);
        ReplayRegistry.registerSkillFormatter(
                dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeRules.ROLE_ID,
                SparkWitchReplayFormatters::natsume);
        ReplayRegistry.registerSkillFormatter(
                dev.caecorthus.sparkwitch.roles.civilian.accountant.AccountantRules.ROLE_ID,
                SparkWitchReplayFormatters::accountant);
    }

    /** 拾荒者:搜刮尸体 / 搜刮获得。Junker: corpse searched, then the item recovered. */
    @Nullable
    private static Text junker(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        NbtCompound data = event.data();
        Map<UUID, ReplayGenerator.PlayerInfo> cache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actor = actor(data);
        if (actor == null) {
            return null;
        }
        Text actorText = ReplayGenerator.formatPlayerName(actor, cache);
        String action = data.getString("action");
        if ("found".equals(action)) {
            return Text.translatable("replay.skill.sparkwitch.ragpicker.found",
                    actorText, Text.literal(data.getString("item")));
        }
        UUID victim = data.containsUuid("victim") ? data.getUuid("victim") : null;
        return victim == null
                ? Text.translatable("replay.skill.sparkwitch.ragpicker.search_unknown", actorText)
                : Text.translatable("replay.skill.sparkwitch.ragpicker.search",
                        actorText, ReplayGenerator.formatPlayerName(victim, cache));
    }

    /** 犯罪推理学家:推测成功 / 失败。Criminal Reasoner: a named killer, right or wrong. */
    @Nullable
    private static Text reasoner(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        NbtCompound data = event.data();
        Map<UUID, ReplayGenerator.PlayerInfo> cache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actor = actor(data);
        UUID victim = data.containsUuid("victim") ? data.getUuid("victim") : null;
        UUID suspect = data.containsUuid("suspect") ? data.getUuid("suspect") : null;
        if (actor == null || victim == null || suspect == null) {
            return null;
        }
        boolean success = data.getBoolean("success");
        return Text.translatable(success
                        ? "replay.skill.sparkwitch.criminal_reasoner.success"
                        : "replay.skill.sparkwitch.criminal_reasoner.fail",
                ReplayGenerator.formatPlayerName(actor, cache),
                ReplayGenerator.formatPlayerName(victim, cache),
                ReplayGenerator.formatPlayerName(suspect, cache));
    }

    /** 探索者:使用乘务员钥匙。Discoverer: the conductor key opened a door. */
    @Nullable
    private static Text discoverer(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        Map<UUID, ReplayGenerator.PlayerInfo> cache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actor = actor(event.data());
        return actor == null ? null : Text.translatable("replay.skill.sparkwitch.discoverer.unlock",
                ReplayGenerator.formatPlayerName(actor, cache));
    }

    /** 橘雪莉: deduction, badge handed out, note received. */
    @Nullable
    private static Text sherry(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        NbtCompound data = event.data();
        Map<UUID, ReplayGenerator.PlayerInfo> cache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actor = actor(data);
        if (actor == null) {
            return null;
        }
        Text actorText = ReplayGenerator.formatPlayerName(actor, cache);
        String action = data.getString("action");
        if ("badge".equals(action)) {
            UUID target = data.containsUuid("target") ? data.getUuid("target") : null;
            return target == null ? null : Text.translatable("replay.skill.sparkwitch.sherry.badge",
                    actorText, ReplayGenerator.formatPlayerName(target, cache));
        }
        if ("note".equals(action)) {
            UUID victim = data.containsUuid("victim") ? data.getUuid("victim") : null;
            return victim == null ? null : Text.translatable("replay.skill.sparkwitch.sherry.note",
                    ReplayGenerator.formatPlayerName(victim, cache), actorText);
        }
        UUID target = data.containsUuid("target") ? data.getUuid("target") : null;
        if (target == null) {
            return null;
        }
        boolean success = data.getBoolean("success");
        // The detective speaks first: "X deduced ... , Y got what was coming".
        // 侦探在前：“X 推演…，Y 得到了应有的惩罚”。
        return Text.translatable(success
                        ? "replay.skill.sparkwitch.sherry.deduction.right"
                        : "replay.skill.sparkwitch.sherry.deduction.wrong",
                actorText, ReplayGenerator.formatPlayerName(target, cache));
    }

    /** 会计: audit, and the two passbook moves. */
    @Nullable
    private static Text accountant(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        NbtCompound data = event.data();
        UUID actor = actor(data);
        if (actor == null) {
            return null;
        }
        Map<UUID, ReplayGenerator.PlayerInfo> cache = ReplayGenerator.getPlayerInfoCache(match);
        Text actorText = ReplayGenerator.formatPlayerName(actor, cache);
        String action = data.getString("action");
        if ("withdraw".equals(action)) {
            return Text.translatable("replay.skill.sparkwitch.accountant.withdraw",
                    actorText, String.valueOf(data.getInt("amount")));
        }
        if ("deposit".equals(action)) {
            return Text.translatable("replay.skill.sparkwitch.accountant.deposit",
                    actorText, String.valueOf(data.getInt("amount")));
        }
        return Text.translatable("replay.skill.sparkwitch.accountant.audit", actorText);
    }

    /** 夏目安安: hypnosis landed, or the target turned out to be immune. */
    @Nullable
    private static Text natsume(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        NbtCompound data = event.data();
        UUID actor = actor(data);
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

    /** 城崎诺亚: what the painting produced, and the wolf watch. */
    @Nullable
    private static Text noa(GameRecordEvent event, GameRecordManager.MatchRecord match, ServerWorld world) {
        NbtCompound data = event.data();
        Map<UUID, ReplayGenerator.PlayerInfo> cache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actor = actor(data);
        if (actor == null) {
            return null;
        }
        Text actorText = ReplayGenerator.formatPlayerName(actor, cache);
        String action = data.getString("action");
        if ("wolf".equals(action)) {
            UUID target = data.containsUuid("target") ? data.getUuid("target") : null;
            return target == null ? null : Text.translatable("replay.skill.sparkwitch.noa.wolf",
                    actorText, ReplayGenerator.formatPlayerName(target, cache));
        }
        String outcome = data.getString("outcome");
        String detail = data.getString("detail");
        if ("sanity".equals(outcome)) {
            return Text.translatable("replay.skill.sparkwitch.noa.sanity", actorText, detail);
        }
        if ("item".equals(outcome)) {
            return Text.translatable("replay.skill.sparkwitch.noa.item", actorText, detail);
        }
        return null;
    }

    @Nullable
    private static UUID actor(NbtCompound data) {
        return data.containsUuid("actor") ? data.getUuid("actor") : null;
    }
}
