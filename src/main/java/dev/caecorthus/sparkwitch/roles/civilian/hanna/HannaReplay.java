package dev.caecorthus.sparkwitch.roles.civilian.hanna;

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
 * Replay for Hanna: skill events under her role id, one action name each and never a server-resolved string, so every
 * viewer reads the line in their own language. An interruption stores the interrupter as the event's target; a float
 * cut short by nobody in particular (an ownerless projectile, a death without a killer) has no target and gets its own
 * line. The parasol and the handkerchief are recorded for whoever used them (owner 2026-11-11: any holder).
 * 汉娜的回放：以其职业 id 记录的技能事件，每条只保存一个动作名，从不保存服务端解析好的字符串，因此每位观看者都以自己的语言
 * 阅读。打断把打断者保存为事件目标；没有明确打断者的情况（无归属的投掷物、没有凶手的死亡）没有目标，使用单独的文案。洋伞与
 * 手帕记录在实际使用者名下（所有者 2026-11-11：谁拿到都能用）。
 */
final class HannaReplay {
    private static final String ACTION = "action";
    private static final String FLOAT = "float";
    private static final String INTERRUPTED = "interrupted";
    private static final String UMBRELLA = "umbrella";
    private static final String HANDKERCHIEF = "handkerchief";
    private static final String HANDKERCHIEF_CLEAR = "handkerchief_clear";
    private static final String KEY_PREFIX = "replay.skill.sparkwitch.hanna.";
    private static boolean registered;

    private HannaReplay() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(HannaRules.ROLE_ID, HannaReplay::format);
    }

    static void recordFloat(ServerPlayerEntity hanna) {
        record(hanna, FLOAT, null);
    }

    static void recordInterrupted(ServerPlayerEntity hanna, @Nullable ServerPlayerEntity interrupter) {
        record(hanna, INTERRUPTED, interrupter);
    }

    /**
     * The parasol works anywhere, so its use is recorded only for a living participant of an ACTIVE round.
     * 洋伞在任何场合都能用，因此只为 ACTIVE 回合中存活的参与者记录。
     */
    static void recordUmbrella(ServerPlayerEntity user) {
        if (HannaHandkerchiefItem.inActiveRound(user)) {
            record(user, UMBRELLA, null);
        }
    }

    /** {@code clearedPenalty}: the handkerchief also ended a float penalty. / 手帕是否同时解除了浮游惩罚。 */
    static void recordHandkerchief(ServerPlayerEntity user, boolean clearedPenalty) {
        record(user, clearedPenalty ? HANDKERCHIEF_CLEAR : HANDKERCHIEF, null);
    }

    private static void record(ServerPlayerEntity actor, String action, @Nullable ServerPlayerEntity target) {
        NbtCompound data = new NbtCompound();
        data.putString(ACTION, action);
        GameRecordManager.recordSkillUse(actor, HannaRules.ROLE_ID, target, data);
    }

    private static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                         ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        var players = ReplayGenerator.getPlayerInfoCache(match);
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), players);
        String action = data.getString(ACTION);
        return switch (action) {
            case INTERRUPTED -> data.containsUuid("target")
                    ? Text.translatable(KEY_PREFIX + "interrupted_by", actor,
                    ReplayGenerator.formatPlayerName(data.getUuid("target"), players))
                    : Text.translatable(KEY_PREFIX + "interrupted", actor);
            case FLOAT, UMBRELLA, HANDKERCHIEF, HANDKERCHIEF_CLEAR -> Text.translatable(KEY_PREFIX + action, actor);
            default -> null;
        };
    }
}
