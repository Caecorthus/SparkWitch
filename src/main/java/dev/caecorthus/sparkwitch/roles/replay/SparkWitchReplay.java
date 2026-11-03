package dev.caecorthus.sparkwitch.roles.replay;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Wires SparkWitch abilities into Wathe's match replay, with per-skill translation keys.
 * <p>Wathe stores the actor and target uuids inside the event's NBT ({@code actor} / {@code target}) and resolves names
 * through {@link ReplayGenerator}. Each skill registers its own formatter, so the replay line goes through a lang key
 * and therefore follows the client's language and any resource pack that overrides it.
 * 把 SparkWitch 的技能接入 Wathe 的对局回放，并按技能注册独立的翻译键。
 * Wathe 把发起者与目标的 uuid 存在事件的 NBT 里（actor / target），名字通过 ReplayGenerator 解析。
 * 每个技能注册自己的格式化器，因此回放行会走 lang 键，从而跟随客户端语言以及任何覆盖它的资源包。
 */
public final class SparkWitchReplay {

    public static final Identifier ATHLETE_DASH = SparkWitch.id("athlete_dash");
    public static final Identifier PLANNER_RECOMMEND = SparkWitch.id("planner_recommend");
    public static final Identifier CONSULTANT_INSPECT = SparkWitch.id("consultant_inspect");
    public static final Identifier CAT_TRANSFORM = SparkWitch.id("schrodingers_cat_transform");
    public static final Identifier PLANNER_TASK_SLIP = SparkWitch.id("planner_task_slip");

    private SparkWitchReplay() {
    }

    public static void register() {
        // Self-only lines: the formatter takes the actor.
        // 只涉及发起者的行：格式化器只取发起者。
        ReplayRegistry.registerSkillFormatter(ATHLETE_DASH,
                (event, match, world) -> selfLine(event, match, "replay.sparkwitch.athlete_dash"));
        ReplayRegistry.registerSkillFormatter(CAT_TRANSFORM,
                (event, match, world) -> selfLine(event, match, "replay.sparkwitch.cat_transform"));
        // Actor + target lines.
        // 发起者 + 目标的两种行。
        ReplayRegistry.registerSkillFormatter(PLANNER_RECOMMEND,
                (event, match, world) -> pairLine(event, match, "replay.sparkwitch.planner_recommend"));
        ReplayRegistry.registerSkillFormatter(CONSULTANT_INSPECT,
                (event, match, world) -> pairLine(event, match, "replay.sparkwitch.consultant_inspect"));
        ReplayRegistry.registerSkillFormatter(PLANNER_TASK_SLIP,
                (event, match, world) -> pairLine(event, match, "replay.sparkwitch.planner_task_slip"));
    }

    private static @Nullable Text selfLine(GameRecordEvent event, GameRecordManager.MatchRecord match, String key) {
        Text actor = nameOf(event, "actor", match);
        return actor == null ? null : Text.translatable(key, actor);
    }

    private static @Nullable Text pairLine(GameRecordEvent event, GameRecordManager.MatchRecord match, String key) {
        Text actor = nameOf(event, "actor", match);
        Text target = nameOf(event, "target", match);
        return actor == null ? null : target == null
                ? Text.translatable(key + ".self", actor)
                : Text.translatable(key, actor, target);
    }

    private static @Nullable Text nameOf(GameRecordEvent event, String field, GameRecordManager.MatchRecord match) {
        NbtCompound data = event.data();
        if (!data.containsUuid(field)) {
            return null;
        }
        UUID uuid = data.getUuid(field);
        return ReplayGenerator.formatPlayerName(uuid, ReplayGenerator.getPlayerInfoCache(match));
    }

    /** Records one skill activation into the match replay. 记录一次技能使用到对局回放。 */
    public static void recordSkill(ServerPlayerEntity player, Identifier skillId,
                                   @Nullable ServerPlayerEntity target, @Nullable NbtCompound extra) {
        try {
            GameRecordManager.recordSkillUse(player, skillId, target, extra);
        } catch (Throwable failure) {
            SparkWitch.LOGGER.warn("[replay] failed to record skill {}: {}", skillId, failure.toString());
        }
    }

    private static void unused(ServerWorld world) {
    }
}
